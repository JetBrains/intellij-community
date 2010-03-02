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

package com.intellij.openapi.application;

import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URL;
import java.util.Enumeration;
import java.util.Properties;
import java.util.PropertyResourceBundle;
import java.util.Set;

public class PathManager {
  @NonNls private static final String PROPERTIES_FILE = "idea.properties.file";
  @NonNls private static final String PROPERTY_SYSTEM_PATH = "idea.system.path";
  @NonNls private static final String PROPERTY_CONFIG_PATH = "idea.config.path";
  @NonNls private static final String PROPERTY_PLUGINS_PATH = "idea.plugins.path";
  @NonNls private static final String PROPERTY_HOME_PATH = "idea.home.path";
  @NonNls private static final String PROPERTY_LOG_PATH = "idea.log.path";

  @NonNls private static String ourHomePath;
  @NonNls private static String ourSystemPath;
  @NonNls private static String ourConfigPath;
  @NonNls private static String ourPluginsPath;
  @NonNls private static String ourLogPath;

  @NonNls private static final String FILE = "file";
  @NonNls private static final String JAR = "jar";
  @NonNls private static final String JAR_DELIMITER = "!";
  @NonNls private static final String PROTOCOL_DELIMITER = ":";
  @NonNls public static final String DEFAULT_OPTIONS_FILE_NAME = "other";
  @NonNls private static final String LIB_FOLDER = "lib";
  @NonNls public static final String PLUGINS_DIRECTORY = "plugins";
  @NonNls private static final String BIN_FOLDER = "bin";
  @NonNls private static final String LOG_DIRECTORY = "log";
  @NonNls private static final String OPTIONS_FOLDER = "options";

  public static String getHomePath() {
    if (ourHomePath != null) return ourHomePath;

    if (System.getProperty(PROPERTY_HOME_PATH) != null) {
      ourHomePath = getAbsolutePath(System.getProperty(PROPERTY_HOME_PATH));
    }
    else {
      final Class aClass = PathManager.class;

      String rootPath = getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
      if (rootPath != null) {
        File root = new File(rootPath).getAbsoluteFile();

        do {
          final String parent = root.getParent();
          if (parent == null) return null;
          assert parent != null : "No parent found for " + root + "; " + BIN_FOLDER + " folder with " +
                                  "idea.properties" + " file not found";
          root = new File(parent).getAbsoluteFile(); // one step back to get folder
        }
        while (root != null && !isIdeaHome(root));

        ourHomePath = root != null ? root.getAbsolutePath() : null;
      }
    }
    try {
      if (!SystemInfo.isFileSystemCaseSensitive) {
        ourHomePath = ourHomePath == null ? null : new File(ourHomePath).getCanonicalPath();
      }
    }
    catch (IOException e) {
      // ignore
    }

    return ourHomePath;
  }

  private static boolean isIdeaHome(final File root) {
    return new File(root, FileUtil.toSystemDependentName("bin/idea.properties")).exists() ||
           new File(root, FileUtil.toSystemDependentName("community/bin/idea.properties")).exists();
  }

  public static String getLibPath() {
    return getHomePath() + File.separator + LIB_FOLDER;
  }

  private static String trimPathQuotes(String path){
    if (!(path != null && !(path.length() < 3))){
      return path;
    }
    if (StringUtil.startsWithChar(path, '\"') && StringUtil.endsWithChar(path, '\"')){
      return path.substring(1, path.length() - 1);
    }
    return path;
  }

  public static String getSystemPath() {
    if (ourSystemPath != null) return ourSystemPath;

    if (System.getProperty(PROPERTY_SYSTEM_PATH) != null) {
      ourSystemPath = getAbsolutePath(trimPathQuotes(System.getProperty(PROPERTY_SYSTEM_PATH)));
    }
    else {
      ourSystemPath = getHomePath() + File.separator + "system";
    }

    try {
      File file = new File(ourSystemPath);
      file.mkdirs();
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    return ourSystemPath;
  }

  public static boolean ensureConfigFolderExists(final boolean createIfNotExists) {
    getConfigPathWithoutDialog();

    File file = new File(ourConfigPath);
    if (createIfNotExists && !file.exists()) {
      file.mkdirs();
      return true;
    }
    return false;
  }

  public static String getConfigPath(boolean createIfNotExists) {
    ensureConfigFolderExists(createIfNotExists);
    return ourConfigPath;
  }

  public static String getConfigPath() {
    return getConfigPath(true);
  }

  private static String  getConfigPathWithoutDialog() {
    if (ourConfigPath != null) return ourConfigPath;

    if (System.getProperty(PROPERTY_CONFIG_PATH) != null) {
      ourConfigPath = getAbsolutePath(trimPathQuotes(System.getProperty(PROPERTY_CONFIG_PATH)));
    }
    else {
      ourConfigPath = getHomePath() + File.separator + "config";
    }
    return ourConfigPath;
  }

  public static String getBinPath() {
    return getHomePath() + File.separator + BIN_FOLDER;
  }

  public static String getOptionsPath() {
    return getConfigPath() + File.separator + OPTIONS_FOLDER;
  }

  public static String getOptionsPathWithoutDialog() {
    return getConfigPathWithoutDialog() + File.separator + OPTIONS_FOLDER;
  }

  public static File getIndexRoot() {
    File file = new File(getSystemPath(), "index");
    try {
      file = file.getCanonicalFile();
    }
    catch (IOException ignored) {
    }
    file.mkdirs();
    return file;
  }

  private static class StringHolder {
    private static final String ourPreinstalledPluginsPath = getHomePath() + File.separatorChar + PLUGINS_DIRECTORY;
  }

  public static String getPreinstalledPluginsPath() {

    return StringHolder.ourPreinstalledPluginsPath;
  }

  public static String getPluginsPath() {
    if (ourPluginsPath == null) {
      if (System.getProperty(PROPERTY_PLUGINS_PATH) != null) {
        ourPluginsPath = getAbsolutePath(trimPathQuotes(System.getProperty(PROPERTY_PLUGINS_PATH)));
      } else {
        ourPluginsPath = getConfigPath() + File.separatorChar + PLUGINS_DIRECTORY;
      }
    }

    return ourPluginsPath;
  }

  public static String getLogPath() {
    if (ourLogPath == null) {
      if (System.getProperty(PROPERTY_LOG_PATH) != null) {
        ourLogPath = getAbsolutePath(trimPathQuotes(System.getProperty(PROPERTY_LOG_PATH)));
      }
      else {
        ourLogPath = getSystemPath() + File.separatorChar + LOG_DIRECTORY;
      }
    }

    return ourLogPath;
  }

  private static String getAbsolutePath(String path) {
    if (path.startsWith("~/") || path.startsWith("~\\")) {
      path = SystemProperties.getUserHome() + path.substring(1);
    }

    return new File(path).getAbsolutePath();
  }

  @NonNls
  public static File getOptionsFile(NamedJDOMExternalizable externalizable) {
    return new File(getOptionsPath()+File.separatorChar+externalizable.getExternalFileName()+".xml");
  }

  @NonNls
  public static File getOptionsFile(@NonNls String fileName) {
    return new File(getOptionsPath()+File.separatorChar+fileName+".xml");
  }

  /**
   * Attempts to detect classpath entry which contains given resource
   */
  @Nullable
  public static String getResourceRoot(Class context, @NonNls String path) {
    URL url = context.getResource(path);
    if (url == null) {
      url = ClassLoader.getSystemResource(path.substring(1));
    }
    if (url == null) {
      return null;
    }
    return extractRoot(url, path);
  }

  /**
   * Attempts to extract classpath entry part from passed URL.
   */
  @NonNls
  private static String extractRoot(URL resourceURL, String resourcePath) {
    if (!(StringUtil.startsWithChar(resourcePath, '/') || StringUtil.startsWithChar(resourcePath, '\\'))) {
      //noinspection HardCodedStringLiteral
      System.err.println("precondition failed: "+resourcePath);
      return null;
    }
    String protocol = resourceURL.getProtocol();
    String resultPath = null;

    if (FILE.equals(protocol)) {
      String path = resourceURL.getFile();
      final String testPath = path.replace('\\', '/');
      final String testResourcePath = resourcePath.replace('\\', '/');
      if (StringUtil.endsWithIgnoreCase(testPath, testResourcePath)) {
        resultPath = path.substring(0, path.length() - resourcePath.length());
      }
    }
    else if (JAR.equals(protocol)) {
      String fullPath = resourceURL.getFile();
      int delimiter = fullPath.indexOf(JAR_DELIMITER);
      if (delimiter >= 0) {
        String archivePath = fullPath.substring(0, delimiter);
        if (StringUtil.startsWithConcatenationOf(archivePath, FILE, PROTOCOL_DELIMITER)) {
          resultPath = archivePath.substring(FILE.length() + PROTOCOL_DELIMITER.length());
        }
      }
    }
    if (resultPath == null) {
      //noinspection HardCodedStringLiteral
      System.err.println("cannot extract: "+resultPath + " from "+resourceURL);
      return null;
    }

    resultPath = StringUtil.trimEnd(resultPath, File.separator);
    resultPath = URLUtil.unescapePercentSequences(resultPath);

    return resultPath;
  }

  @NonNls
  public static File getDefaultOptionsFile() {
    return new File(getOptionsPath(),DEFAULT_OPTIONS_FILE_NAME+".xml");
  }

  public static void loadProperties() {
    File propFile = FileUtil.findFirstThatExist(
      System.getProperty(PROPERTIES_FILE),
      SystemProperties.getUserHome() + "/idea.properties",
      getHomePath() + "/bin/idea.properties",
      getHomePath() + "/community/bin/idea.properties");

    if (propFile != null) {
      InputStream fis = null;
      try {
        fis = new BufferedInputStream(new FileInputStream(propFile));
        final PropertyResourceBundle bundle = new PropertyResourceBundle(fis);
        final Enumeration keys = bundle.getKeys();
        String home = (String)bundle.handleGetObject("idea.home");
        if (home != null && ourHomePath == null) {
          ourHomePath = getAbsolutePath(substitueVars(home));
        }
        final Properties sysProperties = System.getProperties();
        while (keys.hasMoreElements()) {
          String key = (String)keys.nextElement();
          if (sysProperties.getProperty(key, null) == null) { // load the property from the property file only if it is not defined yet
            final String value = substitueVars(bundle.getString(key));
            sysProperties.setProperty(key, value);
          }
        }
      }
      catch (IOException e) {
        //noinspection HardCodedStringLiteral
        System.err.println("Problem reading from property file: " + propFile.getPath());
      }
      finally{
        try {
          if (fis != null) {
            fis.close();
          }
        }
        catch (IOException e) {
        }
      }
    }
  }

  public static String substitueVars(String s) {
    final String ideaHomePath = getHomePath();
    return substituteVars(s, ideaHomePath);
  }

  public static String substituteVars(String s, final String ideaHomePath) {
    if (s == null) return null;
    if (s.startsWith("..")) {
      s = ideaHomePath + File.separatorChar + BIN_FOLDER + File.separatorChar + s;
    }
    s = StringUtil.replace(s, "${idea.home}", ideaHomePath);
    final Properties props = System.getProperties();
    final Set keys = props.keySet();
    for (final Object key1 : keys) {
      String key = (String)key1;
      String value = props.getProperty(key);
      s = StringUtil.replace(s, "${" + key + "}", value);
    }
    return s;
  }

  public static String getPluginTempPath () {
    String systemPath = getSystemPath();

    return systemPath + File.separator + PLUGINS_DIRECTORY;
  }

  public static File findFileInLibDirectory(@NotNull String relativePath) {
    File file = new File(getLibPath() + File.separator + relativePath);
    if (file.exists()) {
      return file;
    }
    return new File(getHomePath() + File.separator + "community" + File.separator + "lib" + File.separator + relativePath);
  }
}
