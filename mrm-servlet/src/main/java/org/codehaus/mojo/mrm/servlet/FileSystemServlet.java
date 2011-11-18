/*
 * Copyright 2011 Stephen Connolly
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codehaus.mojo.mrm.servlet;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.mojo.mrm.api.DefaultDirectoryEntry;
import org.codehaus.mojo.mrm.api.DirectoryEntry;
import org.codehaus.mojo.mrm.api.Entry;
import org.codehaus.mojo.mrm.api.FileEntry;
import org.codehaus.mojo.mrm.api.FileSystem;
import org.codehaus.mojo.mrm.impl.MemoryFileSystem;
import org.codehaus.mojo.mrm.impl.Utils;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Servlet that serves a {@link FileSystem}.
 */
public class FileSystemServlet
    extends HttpServlet
{

    /**
     * Width of the name column in the HTML view.
     */
    private static final int NAME_COL_WIDTH = 50;

    /**
     * Width of the size column in the HTML view.
     */
    private static final int SIZE_COL_WIDTH = 20;

    /**
     * The file system that we are serving.
     */
    private FileSystem fileSystem;

    /**
     * Default constructor.
     */
    public FileSystemServlet()
    {
        this.fileSystem = new MemoryFileSystem();
    }

    /**
     * Constructor that takes a specific file system instance.
     *
     * @param fileSystem the file systen to serve.
     */
    public FileSystemServlet( FileSystem fileSystem )
    {
        this.fileSystem = fileSystem;
    }

    /**
     * {@inheritDoc}
     */
    protected void doGet( HttpServletRequest req, HttpServletResponse resp )
        throws ServletException, IOException
    {
        String path = req.getPathInfo();
        String context;
        if ( path == null )
        {
            path = req.getServletPath();
            context = req.getContextPath();
        }
        else
        {
            context = req.getContextPath() + req.getServletPath();
        }
        Entry entry = fileSystem.get( path );
        if ( entry instanceof FileEntry )
        {
            FileEntry fileEntry = (FileEntry) entry;
            long size = fileEntry.getSize();
            if ( size >= 0 && size < Integer.MAX_VALUE )
            {
                resp.setContentLength( (int) size );
            }
            resp.setContentType( getServletContext().getMimeType( fileEntry.getName() ) );
            InputStream source = null;
            OutputStream destination = null;
            try
            {
                source = fileEntry.getInputStream();
                destination = resp.getOutputStream();
                IOUtils.copy( source, destination );
            }
            finally
            {
                IOUtils.closeQuietly( source );
                IOUtils.closeQuietly( destination );
            }
            return;
        }
        else if ( entry instanceof DirectoryEntry )
        {
            if ( !path.endsWith( "/" ) )
            {
                resp.sendRedirect( entry.getName() + "/" );
                return;
            }
            DirectoryEntry dirEntry = (DirectoryEntry) entry;
            Entry[] entries = fileSystem.listEntries( dirEntry );
            resp.setContentType( "text/html" );
            PrintWriter w = resp.getWriter();
            w.println( "<html>" );
            w.println( "  <head>" );
            w.println( "    <title>Index of " + context + path + "</title>" );
            w.println( "    <meta http-equiv=\"Content-Type\" repository=\"text/html; charset=utf-8\"/>" );
            w.println( "</head>" );
            w.println( "<body>" );
            w.println( "<h1>Index of " + context + path + "</h1>" );
            w.println( "  <hr/>" );
            w.write( "<pre>" );

            if ( dirEntry.getParent() != null )
            {
                w.println( "<a href='../'>../</a>" );
            }
            SimpleDateFormat format = new SimpleDateFormat( "dd-MMM-yyyy hh:mm" );
            if ( entries != null )
            {
                for ( int i = 0; i < entries.length; i++ )
                {
                    final String childName = entries[i].getName();
                    boolean directory = entries[i] instanceof DirectoryEntry;
                    if ( directory )
                    {
                        String dirName = childName + "/";
                        w.write(
                            "<a href=\"./" + Utils.urlEncodePathSegment( childName ) + "/\">" + formatName( dirName )
                                + "</a>" + StringUtils.repeat( " ",
                                                               Math.max( 0, NAME_COL_WIDTH - dirName.length() ) ) );
                    }
                    else
                    {
                        w.write(
                            "<a href=\"./" + Utils.urlEncodePathSegment( childName ) + "\">" + formatName( childName )
                                + "</a>" + StringUtils.repeat( " ",
                                                               Math.max( 0, NAME_COL_WIDTH - childName.length() ) ) );
                    }

                    long timestamp = 0;
                    try
                    {
                        timestamp = entries[i].getLastModified();
                    }
                    catch ( IOException e )
                    {
                        // ignore
                    }

                    w.write( " " );
                    w.write( format.format( timestamp != -1 ? new Date( timestamp ) : new Date() ) );
                    if ( directory )
                    {
                        w.println( StringUtils.leftPad( "-", SIZE_COL_WIDTH ) );
                    }
                    else if ( entries[i] instanceof FileEntry )
                    {
                        FileEntry fileEntry = (FileEntry) entries[i];
                        try
                        {
                            long size = fileEntry.getSize();
                            if ( size >= 0 )
                            {
                                w.println( StringUtils.leftPad( Long.toString( size ), SIZE_COL_WIDTH ) );
                            }
                            else
                            {
                                w.println( StringUtils.leftPad( "-", SIZE_COL_WIDTH ) );
                            }
                        }
                        catch ( IOException e )
                        {
                            w.println( StringUtils.leftPad( "-", SIZE_COL_WIDTH ) );
                        }
                    }
                    else
                    {
                        w.println( StringUtils.leftPad( "-", SIZE_COL_WIDTH ) );
                    }
                }
            }
            w.write( "</pre>" );
            w.println( "  <hr/>" );
            w.println( "</body>" );
            w.println( "</html>" );
            return;
        }

        resp.sendError( HttpURLConnection.HTTP_NOT_FOUND );
    }

    /**
     * {@inheritDoc}
     */
    protected void doPut( HttpServletRequest req, HttpServletResponse resp )
        throws ServletException, IOException
    {
        String path = req.getPathInfo();
        String context;
        if ( path == null )
        {
            path = req.getServletPath();
            context = req.getContextPath();
        }
        else
        {
            context = req.getContextPath() + req.getServletPath();
        }
        if ( path.endsWith( "/" ) )
        {
            resp.sendError( HttpURLConnection.HTTP_BAD_METHOD );
            return;
        }
        if ( path.startsWith( "/" ) )
        {
            path = path.substring( 1 );
        }
        String[] parts = path.split( "/" );
        if ( parts.length == 0 )
        {
            resp.sendError( HttpURLConnection.HTTP_BAD_METHOD );
            return;
        }
        String name = parts[parts.length - 1];
        if ( StringUtils.isEmpty( name ) )
        {
            resp.sendError( HttpURLConnection.HTTP_BAD_METHOD );
            return;
        }
        DirectoryEntry parent = fileSystem.getRoot();
        for ( int i = 0; i < parts.length - 1; i++ )
        {
            parent = new DefaultDirectoryEntry( fileSystem, parent, parts[i] );
        }
        ServletInputStream inputStream = null;
        try
        {
            inputStream = req.getInputStream();
            FileEntry put = fileSystem.put( parent, name, inputStream );
            if ( put != null )
            {
                resp.setStatus( HttpURLConnection.HTTP_OK );
                return;
            }
        }
        finally
        {
            IOUtils.closeQuietly( inputStream );
        }
        resp.sendError( HttpURLConnection.HTTP_BAD_METHOD );
    }

    /**
     * {@inheritDoc}
     */
    protected void doDelete( HttpServletRequest req, HttpServletResponse resp )
        throws ServletException, IOException
    {
        String path = req.getPathInfo();
        String context;
        if ( path == null )
        {
            path = req.getServletPath();
            context = req.getContextPath();
        }
        else
        {
            context = req.getContextPath() + req.getServletPath();
        }
        Entry entry = fileSystem.get( path );
        if ( entry == null )
        {
            resp.setStatus( HttpURLConnection.HTTP_OK );
            return;
        }
        try
        {
            fileSystem.remove( entry );
            resp.setStatus( HttpURLConnection.HTTP_OK );
        }
        catch ( UnsupportedOperationException e )
        {
            resp.sendError( HttpURLConnection.HTTP_BAD_METHOD );
        }
    }

    /**
     * Formats a name for the fixed width layout of the html index.
     *
     * @param name the name.
     * @return the name or the name shortened to 50 characters.
     */
    private static String formatName( String name )
    {
        if ( name.length() < NAME_COL_WIDTH )
        {
            return name;
        }
        return name.substring( 0, NAME_COL_WIDTH - 1 ) + ">";
    }

}
