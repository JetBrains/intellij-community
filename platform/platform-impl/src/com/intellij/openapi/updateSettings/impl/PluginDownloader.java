/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * User: anna
 * Date: 10-Aug-2007
 */
package com.intellij.openapi.updateSettings.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.RepositoryHelper;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.UrlConnectionUtil;
import com.intellij.util.io.ZipUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class PluginDownloader {

  private static final Logger LOG = Logger.getInstance("#" + PluginDownloader.class.getName());

  @NonNls private static final String FILENAME = "filename=";

  private final String myPluginId;
  private final String myPluginUrl;
  private String myPluginVersion;

  private String myFileName;
  private String myPluginName;

  private File myFile;
  private File myOldFile;

  public PluginDownloader(final String pluginId, final String pluginUrl, final String pluginVersion) {
    myPluginId = pluginId;
    myPluginUrl = pluginUrl;
    myPluginVersion = pluginVersion;
  }

  public PluginDownloader(final String pluginId,
                          final String pluginUrl,
                          final String pluginVersion,
                          final String fileName,
                          final String pluginName) {
    myPluginId = pluginId;
    myPluginUrl = pluginUrl;
    myPluginVersion = pluginVersion;
    myFileName = fileName;
    myPluginName = pluginName;
  }

  public boolean prepareToInstall() throws IOException {
    return prepareToInstall(new ProgressIndicatorBase());
  }

  public boolean prepareToInstall(ProgressIndicator pi) throws IOException {
    IdeaPluginDescriptor ideaPluginDescriptor = null;
    if (PluginManager.isPluginInstalled(PluginId.getId(myPluginId))) {
      //store old plugins file
      ideaPluginDescriptor = PluginManager.getPlugin(PluginId.getId(myPluginId));
      LOG.assertTrue(ideaPluginDescriptor != null);
      if (myPluginVersion != null && StringUtil.compareVersionNumbers(ideaPluginDescriptor.getVersion(), myPluginVersion) >= 0) {
        LOG.info("Plugin " + myPluginId + ": current version (max) " + myPluginVersion);
        return false;
      }
      myOldFile = ideaPluginDescriptor.getPath();
    }
    // download plugin
    String errorMessage = IdeBundle.message("unknown.error");
    try {
      myFile = downloadPlugin(pi);
    }
    catch (IOException ex) {
      myFile = null;
      errorMessage = ex.getMessage();
    }
    if (myFile == null) {
      final String errorMessage1 = errorMessage;
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          Messages.showErrorDialog(IdeBundle.message("error.plugin.was.not.installed", getPluginName(), errorMessage1),
                                   IdeBundle.message("title.failed.to.download"));
        }
      });
      return false;
    }

    IdeaPluginDescriptorImpl descriptor = PluginManager.loadDescriptorFromJar(myFile);
    if (descriptor == null) {
      if (myFile.getName().endsWith(".zip")) {
        final File outputDir = FileUtil.createTempDirectory("plugin", "");
        try {
          ZipUtil.extract(myFile, outputDir, new FilenameFilter() {
            public boolean accept(final File dir, final String name) {
              return true;
            }
          });
          descriptor = PluginManager.loadDescriptor(new File(outputDir, FileUtil.getNameWithoutExtension(myFile)), PluginManager.PLUGIN_XML);
        }
        finally {
          FileUtil.delete(outputDir);
        }
      }
    }
    if (descriptor != null) {
      myPluginVersion = descriptor.getVersion();
      if (ideaPluginDescriptor != null && StringUtil.compareVersionNumbers(ideaPluginDescriptor.getVersion(), descriptor.getVersion()) >= 0) {
        LOG.info("Plugin " + myPluginId + ": current version (max) " + myPluginVersion);
        return false; //was not updated
      }
      final BuildNumber currentBuildNumber = ApplicationInfo.getInstance().getBuild();
      final BuildNumber sinceBuild = BuildNumber.fromString(descriptor.getSinceBuild());
      if (sinceBuild != null && sinceBuild.compareTo(currentBuildNumber) > 0) {
        return false;
      }
      final BuildNumber untilBuild = BuildNumber.fromString(descriptor.getUntilBuild());
      if (untilBuild != null && untilBuild.compareTo(currentBuildNumber) < 0) {
        return false;
      }
    }
    return true;
  }

  public void install() throws IOException {
    LOG.assertTrue(myFile != null);
    if (myOldFile != null) {
      // add command to delete the 'action script' file
      StartupActionScriptManager.ActionCommand deleteOld = new StartupActionScriptManager.DeleteCommand(myOldFile);
      StartupActionScriptManager.addActionCommand(deleteOld);
    }

    //noinspection HardCodedStringLiteral
    if (myFile.getName().endsWith(".jar")) {
      // add command to copy file to the IDEA/plugins path
      StartupActionScriptManager.ActionCommand copyPlugin =
        new StartupActionScriptManager.CopyCommand(myFile, new File(PathManager.getPluginsPath() + File.separator + myFile.getName()));
      StartupActionScriptManager.addActionCommand(copyPlugin);
    }
    else {
      // add command to unzip file to the IDEA/plugins path
      String unzipPath;
      if (ZipUtil.isZipContainsFolder(myFile)) {
        unzipPath = PathManager.getPluginsPath();
      }
      else {
        unzipPath = PathManager.getPluginsPath() + File.separator + getPluginName();
      }

      StartupActionScriptManager.ActionCommand unzip = new StartupActionScriptManager.UnzipCommand(myFile, new File(unzipPath));
      StartupActionScriptManager.addActionCommand(unzip);
    }

    // add command to remove temp plugin file
    StartupActionScriptManager.ActionCommand deleteTemp = new StartupActionScriptManager.DeleteCommand(myFile);
    StartupActionScriptManager.addActionCommand(deleteTemp);
  }

  private File downloadPlugin(ProgressIndicator pi) throws IOException {
    HttpURLConnection connection = (HttpURLConnection)new URL(myPluginUrl).openConnection();
    try
    {
      pi.setText(IdeBundle.message("progress.connecting"));

      InputStream is = UrlConnectionUtil.getConnectionInputStream(connection, pi);

      if (is == null) {
        throw new IOException("Failed to open connection");
      }

      pi.setText(IdeBundle.message("progress.downloading.plugin", getPluginName()));

      final File pluginsTemp = new File(PathManager.getPluginTempPath());

      if (!pluginsTemp.exists()) {
        pluginsTemp.mkdirs();
      }

      File file = File.createTempFile("plugin", "download", pluginsTemp);

      int responseCode = connection.getResponseCode();
      switch (responseCode) {
        case HttpURLConnection.HTTP_OK:
          break;
        default:
          // some problems
          throw new IOException(IdeBundle.message("error.connection.failed.with.http.code.N", responseCode));
      }

      pi.setIndeterminate(connection.getContentLength() == -1);

      OutputStream fos = null;
      try {
        fos = new BufferedOutputStream(new FileOutputStream(file, false));
        StreamUtil.copyStreamContent(is, fos);
      }
      finally {
        if (fos != null) {
          fos.close();
        }
        is.close();
      }
      if (myFileName == null) {
        String contentDisposition = connection.getHeaderField("Content-Disposition");
        if (contentDisposition == null || contentDisposition.indexOf(FILENAME) < 0) {
          // try to find filename in URL
          String usedURL = connection.getURL().toString();
          int startPos = usedURL.lastIndexOf("/");

          myFileName = usedURL.substring(startPos + 1);
          if (myFileName.length() == 0 || myFileName.contains("?")) {
            myFileName = myPluginUrl.substring(myPluginUrl.lastIndexOf("/") + 1);
          }
        }
        else {
          int startIdx = contentDisposition.indexOf(FILENAME);
          myFileName = contentDisposition.substring(startIdx + FILENAME.length(), contentDisposition.length());
          // according to the HTTP spec, the filename is a quoted string, but some servers don't quote it
          // for example: http://www.jspformat.com/Download.do?formAction=d&id=8
          if (myFileName.startsWith("\"") && myFileName.endsWith("\"")) {
            myFileName = myFileName.substring(1, myFileName.length()-1);
          }
          if (myFileName.indexOf('\\') >= 0 || myFileName.indexOf('/') >= 0 || myFileName.indexOf(File.separatorChar) >= 0 ||
              myFileName.indexOf('\"') >= 0) {
            // invalid path name passed by the server - fail to download
            FileUtil.delete(file);
            throw new IOException("Invalid filename returned by server");
          }
        }
      }

      File newFile = new File (file.getParentFile(), myFileName);
      FileUtil.rename(file, newFile);
      return newFile;
    }
    finally {
      connection.disconnect();
    }
  }

  public String getPluginId() {
    return myPluginId;
  }

  public String getFileName() {
    if (myFileName == null) {
      myFileName = myPluginUrl.substring(myPluginUrl.lastIndexOf("/") + 1);
    }
    return myFileName;
  }

  public String getPluginName() {
    if (myPluginName == null) {
      myPluginName = FileUtil.getNameWithoutExtension(getFileName());
    }
    return myPluginName;
  }

  public String getPluginVersion() {
    return myPluginVersion;
  }

  /**
   * Updates given plugin from Repository
   * @param pluginId given plugin id
   * @param pluginVersion available version or null if plugin must be uploaded even if current version is greater than uploading
   * @throws IOException
   */
  public static void updateFromRepository(final String pluginId, final @Nullable String pluginVersion) throws IOException {
    @NonNls final String url =
      RepositoryHelper.DOWNLOAD_URL + URLEncoder.encode(pluginId, "UTF8") + "&build=" + ApplicationInfo.getInstance().getBuild().asString();
    final PluginDownloader downloader = new PluginDownloader(pluginId, url, pluginVersion);
    if (downloader.prepareToInstall()) {
      downloader.install();
    }
  }
}
