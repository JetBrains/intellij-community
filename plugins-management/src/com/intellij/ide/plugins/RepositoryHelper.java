package com.intellij.ide.plugins;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
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
  //public static final String REPOSITORY_HOST = "http://unit-038:8080/plug";
  @NonNls public static final String REPOSITORY_LIST_URL = getRepositoryHost() + "/plugins/list/";
  @NonNls public static final String DOWNLOAD_URL = getRepositoryHost() + "/pluginManager?action=download&id=";

  @NonNls private static final String FILENAME = "filename=";
  @NonNls public static final String extPluginsFile = "availables.xml";

  public static ArrayList<IdeaPluginDescriptor> process(JLabel label) throws IOException, ParserConfigurationException, SAXException {
    ArrayList<IdeaPluginDescriptor> plugins = null;
    try {
      String buildNumber = extractBuildNumber();
      @NonNls String url = REPOSITORY_LIST_URL + "?build=" + buildNumber;

      setLabelText(label, IdeBundle.message("progress.connecting.to.plugin.manager", getRepositoryHost()));
      HttpConfigurable.getInstance().prepareURL(getRepositoryHost());
//      if( !pi.isCanceled() )
      {
        RepositoryContentHandler handler = new RepositoryContentHandler();
        HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();

        setLabelText(label, IdeBundle.message("progress.waiting.for.reply.from.plugin.manager", getRepositoryHost()));

        InputStream is = getConnectionInputStream(connection);
        if (is != null) {
          setLabelText(label, IdeBundle.message("progress.downloading.list.of.plugins"));
          File temp = createLocalPluginsDescriptions();
          readPluginsStream(temp, is, handler);

          plugins = handler.getPluginsList();
        }
      }
    }
    catch (RuntimeException e) {
      e.printStackTrace();
      if (e.getCause() == null || !(e.getCause() instanceof InterruptedException)) {
      }
    }
    return plugins;
  }

  private static void setLabelText(final JLabel label, final String message) {
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        label.setText(message);
      }
    });
  }


  public static InputStream getConnectionInputStream(URLConnection connection) {
    try {
      return connection.getInputStream();
    }
    catch (IOException e) {
      return null;
    }
  }

  public static File createLocalPluginsDescriptions() throws IOException {
    File basePath = new File(PathManager.getPluginsPath());
    basePath.mkdirs();

    File temp = new File(basePath, extPluginsFile);
    if (temp.exists()) {
      FileUtil.delete(temp);
    }
    temp.createNewFile();
    return temp;
  }

  public static void readPluginsStream(File temp, InputStream is, RepositoryContentHandler handler)
    throws SAXException, IOException, ParserConfigurationException {
    SAXParser parser = SAXParserFactory.newInstance().newSAXParser();
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(temp, false);
      byte[] buffer = new byte[1024];
      do {
        int size = is.read(buffer);
        if (size == -1) break;
        fos.write(buffer, 0, size);
      }
      while (true);
      fos.close();
      fos = null;

      parser.parse(temp, handler);
    }
    finally {
      if (fos != null) {
        fos.close();
      }
    }
  }

  public static String extractBuildNumber() {
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

  public static String getRepositoryHost() {
    return ApplicationInfoImpl.getShadowInstance().getPluginManagerUrl();
  }
}
