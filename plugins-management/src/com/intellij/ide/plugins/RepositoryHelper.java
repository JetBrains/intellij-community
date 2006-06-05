package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressStream;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.io.UrlConnectionUtil;
import org.jetbrains.annotations.NonNls;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Mar 28, 2003
 * Time: 12:56:26 AM
 * To change this template use Options | File Templates.
 */
public class RepositoryHelper {
  //private static final Logger LOG = com.intellij.openapi.diagnostic.Logger.getInstance("#com.intellij.ide.plugins.RepositoryHelper");
  @NonNls public static final String REPOSITORY_HOST = "http://plugins.intellij.net";
  //public static final String REPOSITORY_HOST = "http://unit-038:8080/plug";
  @NonNls public static final String REPOSITORY_LIST_URL = REPOSITORY_HOST + "/plugins/list/";
  @NonNls public static final String DOWNLOAD_URL = REPOSITORY_HOST + "/pluginManager?action=download&id=";
  @NonNls public static final String REPOSITORY_LIST_SYSTEM_ID = REPOSITORY_HOST + "/plugin-repository.dtd";

  @NonNls private static final String FILENAME = "filename=";
  @NonNls public static final String extPluginsFile = "availables.xml";

  public static ArrayList<IdeaPluginDescriptor> Process( JLabel label )
    throws IOException, ParserConfigurationException, SAXException
  {
    ArrayList<IdeaPluginDescriptor> plugins = null;
    try {
      String buildNumber = RepositoryHelper.ExtractBuildNumber();
      //noinspection HardCodedStringLiteral
      String url = RepositoryHelper.REPOSITORY_LIST_URL + "?build=" + buildNumber;

      label.setText(IdeBundle.message("progress.connecting.to.plugin.manager", RepositoryHelper.REPOSITORY_HOST));
      HttpConfigurable.getInstance().prepareURL(RepositoryHelper.REPOSITORY_HOST);
//      if( !pi.isCanceled() )
      {
        RepositoryContentHandler handler = new RepositoryContentHandler();
        HttpURLConnection connection = (HttpURLConnection)new URL (url).openConnection();

        label.setText(IdeBundle.message("progress.waiting.for.reply.from.plugin.manager", RepositoryHelper.REPOSITORY_HOST));

        InputStream is = RepositoryHelper.getConnectionInputStream( connection );
        if (is != null)
        {
          label.setText(IdeBundle.message("progress.downloading.list.of.plugins"));
          File temp = RepositoryHelper.CreateLocalPluginsDescriptions();
          RepositoryHelper.ReadPluginsStream( temp, is, handler );

          plugins = handler.getPluginsList();
        }
      }
    }
    catch (RuntimeException e)
    {
      if( e.getCause() == null || !( e.getCause() instanceof InterruptedException) )
      {
      }
    }
    return plugins;
  }

  public static File downloadPlugin (PluginNode pluginNode, boolean packet, long count, long available) throws IOException
  {
    String buildNumber = ExtractBuildNumber();

    //noinspection HardCodedStringLiteral
    String url = DOWNLOAD_URL +
                 URLEncoder.encode(pluginNode.getName(), "UTF8") +
                 "&build=" + buildNumber;
    HttpURLConnection connection = (HttpURLConnection)new URL (url).openConnection();
    try
    {
      final ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();
      pi.setText(IdeBundle.message("progress.connecting"));

      InputStream is = UrlConnectionUtil.getConnectionInputStream(connection, pi);

      if (is == null) {
        throw new IOException("Failed to open connection");
      }

      pi.setText(IdeBundle.message("progress.downloading.plugin", pluginNode.getName()));
      //noinspection HardCodedStringLiteral
      File file = File.createTempFile("plugin", "download",
                                      new File (PathManagerEx.getPluginTempPath()));
      OutputStream fos = new BufferedOutputStream(new FileOutputStream(file, false));

      int responseCode = connection.getResponseCode();
      switch (responseCode) {
        case HttpURLConnection.HTTP_OK:
          break;
        default:
          // some problems
          throw new IOException(IdeBundle.message("error.connection.failed.with.http.code.N", responseCode));
      }

      if (pluginNode.getSize().equals("-1")) {
        if (connection.getContentLength() == -1)
          pi.setIndeterminate(true);
        else
          pluginNode.setSize(Integer.toString(connection.getContentLength()));
      }

      boolean cleanFile = true;

      try {
        is = new ProgressStream(packet ? count : 0, packet ? available : Integer.valueOf(pluginNode.getSize()).intValue(),
                                is, pi);
        int c;
        while ((c = is.read()) != -1) {
          if (pi.isCanceled())
            throw new RuntimeException(new InterruptedException());

          fos.write(c);
        }

        cleanFile = false;
      } catch (RuntimeException e) {
        if (e.getCause() != null && e.getCause() instanceof InterruptedException)
          return null;
        else
          throw e;
      } finally {
        fos.close();
        if (cleanFile)
          file.delete();
      }

      String fileName = null;
      //noinspection HardCodedStringLiteral
      String contentDisposition = connection.getHeaderField("Content-Disposition");
      if (contentDisposition == null) {
        // try to find filename in URL
        String usedURL = connection.getURL().toString();
        int startPos = usedURL.lastIndexOf("/");

        fileName = usedURL.substring(startPos + 1);
        if (fileName.length() == 0) {
          throw new IOException("No filename returned by server");
        }

      } else {
        int startIdx = contentDisposition.indexOf(FILENAME);
        if (startIdx != -1) {
          fileName = contentDisposition.substring(startIdx + FILENAME.length(), contentDisposition.length());
          // according to the HTTP spec, the filename is a quoted string, but some servers don't quote it
          // for example: http://www.jspformat.com/Download.do?formAction=d&id=8
          if (fileName.startsWith("\"") && fileName.endsWith("\"")) {
            fileName = fileName.substring(1, fileName.length()-1);
          }
          if (fileName.indexOf('\\') >= 0 || fileName.indexOf('/') >= 0 || fileName.indexOf(File.separatorChar) >= 0 ||
              fileName.indexOf('\"') >= 0) {
            // invalid path name passed by the server - fail to download
            FileUtil.delete(file);
            throw new IOException("Invalid filename returned by server");
          }
        }
        else {
          // invalid Content-Disposition header passed by the server - fail to download
          FileUtil.delete(file);
          throw new IOException("Invalid filename returned by server");
        }
      }

      File newFile = new File (file.getParentFile(), fileName);
      FileUtil.rename(file, newFile);
      return newFile;
    }
    finally {
      connection.disconnect();
    }
  }

  public static InputStream getConnectionInputStream (URLConnection connection )
  {
    try
    {
      return connection.getInputStream();
    }
    catch( IOException e )
    {
      return null;
    }
  }

  public static File CreateLocalPluginsDescriptions() throws IOException
  {
    File basePath = new File( PathManager.getPluginsPath() );
    basePath.mkdirs();

    File temp = new File( basePath, extPluginsFile );
    if( temp.exists() )
    {
        FileUtil.delete(temp);
    }
    temp.createNewFile();
    return temp;
  }

  public static void  ReadPluginsStream( File temp,
                                         InputStream is,
                                         RepositoryContentHandler handler,
                                         ProgressIndicator pi)
    throws SAXException, IOException, ParserConfigurationException
  {
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    FileOutputStream fos = null;
    ProgressStream ps = null;
    try {
      fos = new FileOutputStream(temp, false);
      ps = new ProgressStream(is, pi);
      byte [] buffer = new byte [1024];
      do {
        int size = ps.read(buffer);
        if (size == -1)
          break;
        fos.write(buffer, 0, size);
      } while (true);
      fos.close();

      parser.parse(temp, handler);
    } finally {
      if( fos != null ) {
        fos.close();
      }
      if( ps != null )  {
        ps.close();
      }
    }
  }

  public static void  ReadPluginsStream( File temp, InputStream is,
                                         RepositoryContentHandler handler )
    throws SAXException, IOException, ParserConfigurationException
  {
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(temp, false);
      byte [] buffer = new byte [1024];
      do {
        int size = is.read(buffer);
        if (size == -1)
          break;
        fos.write(buffer, 0, size);
      } while (true);
      fos.close();
      fos = null;

      parser.parse(temp, handler);
    } finally {
      if( fos != null ) {
        fos.close();
      }
    }
  }

  public static String  ExtractBuildNumber()
  {
    String build;
    ApplicationInfoEx ideInfo = (ApplicationInfoEx)ApplicationInfo.getInstance();
    try {
      build = Integer.valueOf(ideInfo.getBuildNumber()).toString();
    }
    catch (NumberFormatException e) {
      build = "3000";
    }
    return build;
  }
}
