/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.URLUtil;
import com.sun.jna.TypeMapper;
import com.sun.jna.platform.FileUtils;
import gnu.trove.THashSet;
import org.apache.log4j.Appender;
import org.apache.oro.text.regex.PatternMatcher;
import org.jdom.Document;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.picocontainer.PicoContainer;

import java.io.*;
import java.net.URL;
import java.util.*;

public class PathManager {
  @NonNls public static final String PROPERTIES_FILE = "idea.properties.file";
  @NonNls public static final String PROPERTY_SYSTEM_PATH = "idea.system.path";
  @NonNls public static final String PROPERTY_CONFIG_PATH = "idea.config.path";
  @NonNls public static final String PROPERTY_PLUGINS_PATH = "idea.plugins.path";
  @NonNls public static final String PROPERTY_HOME_PATH = "idea.home.path";
  @NonNls public static final String PROPERTY_LOG_PATH = "idea.log.path";
  @NonNls public static final String PROPERTY_PATHS_SELECTOR = "idea.paths.selector";
  @NonNls public static final String PROPERTY_ORIGINAL_WORKING_DIR = "original.working.dir";

  @NonNls private static String ourHomePath;
  @NonNls private static String ourSystemPath;
  @NonNls private static String ourConfigPath;
  @NonNls private static String ourPluginsPath;
  @NonNls private static String ourLogPath;

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
      ourHomePath = getHomePathFor(PathManager.class);
      if (ourHomePath == null) {
        if (SystemInfo.isMac) {
          throw new RuntimeException("Could not find installation home path. Please reinstall the software.");
        }
        throw new RuntimeException("Could not find IDEA home path. Please make sure bin/idea.properties is present in the installation directory.");
      }
    }
    try {
      if (!SystemInfo.isFileSystemCaseSensitive) {
        ourHomePath = new File(ourHomePath).getCanonicalPath();
      }
    }
    catch (IOException e) {
      // ignore
    }

    return ourHomePath;
  }

  @Nullable
  public static String getHomePathFor(Class aClass) {
    String rootPath = getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    if (rootPath != null) {
      File root = new File(rootPath).getAbsoluteFile();

      do {
        final String parent = root.getParent();
        if (parent == null) return null;
        root = new File(parent).getAbsoluteFile(); // one step back to get folder
      }
      while (!isIdeaHome(root));

      return root.getAbsolutePath();
    }
    return null;
  }

  private static boolean isIdeaHome(final File root) {
    return new File(root, FileUtil.toSystemDependentName("bin/idea.properties")).exists() ||
           new File(root, FileUtil.toSystemDependentName("community/bin/idea.properties")).exists() ||
           new File(root, FileUtil.toSystemDependentName("Contents/Info.plist")).exists();  // MacOS bundle doesn't include idea.properties
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
    else if (getPathsSelector() != null) {
      final String selector = getPathsSelector();
      // Mac: ~/Library/Caches/@@selector@@
      // Others: ~/.@@selector@@/system
      ourSystemPath = SystemProperties.getUserHome() + (SystemInfo.isMac ? "/Library/Caches/" + selector
                                                                         : File.separator + "." + selector + File.separator + "system");
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

    final File file = new File(ourConfigPath);
    if (createIfNotExists && !file.exists()) {
      return file.mkdirs();  // shouldn't copy configs if failed to create new directory
    }

    return false;
  }

  @NotNull
  public static String getConfigPath(boolean createIfNotExists) {
    ensureConfigFolderExists(createIfNotExists);
    return ourConfigPath;
  }

  @NotNull
  public static String getConfigPath() {
    return getConfigPath(true);
  }

  @NotNull
  private static String getConfigPathWithoutDialog() {
    if (ourConfigPath != null) return ourConfigPath;

    if (System.getProperty(PROPERTY_CONFIG_PATH) != null) {
      ourConfigPath = getAbsolutePath(trimPathQuotes(System.getProperty(PROPERTY_CONFIG_PATH)));
    }
    else if (getPathsSelector() != null) {
      final String selector = getPathsSelector();
      // Mac: ~/Library/Preferences/@@selector@@
      // Others: ~/.@@selector@@/config
      ourConfigPath = getDefaultConfigPathFor(selector);
    }
    else {
      ourConfigPath = getHomePath() + File.separator + "config";
    }
    return ourConfigPath;
  }

  @NotNull
  public static String getDefaultConfigPathFor(String selector) {
    return SystemProperties.getUserHome() + (SystemInfo.isMac ? "/Library/Preferences/" + selector
                                                              : File.separator + "." + selector + File.separator + "config");
  }

  public static String getPathsSelector() {
    return System.getProperty(PROPERTY_PATHS_SELECTOR);
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
    File file = new File(getIndexRootDir());
    try {
      file = file.getCanonicalFile();
    }
    catch (IOException ignored) {
    }
    file.mkdirs();
    return file;
  }

  private static String getIndexRootDir() {
    String dir = System.getProperty("index_root_path");
    return dir == null ? getSystemPath() + "/index" : dir;
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
      }
      else if (SystemInfo.isMac && getPathsSelector() != null) {
        // Mac: ~//Library/Application Support/@@selector@@
        ourPluginsPath = SystemProperties.getUserHome() + "/Library/Application Support/" + getPathsSelector();
      }
      else {
        // Others: @@config_path@@/plugins
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
      else if (SystemInfo.isMac && getPathsSelector() != null) {
        // Mac: ~/Library/Logs/@@selector@@
        ourLogPath = SystemProperties.getUserHome() + "/Library/Logs/" + getPathsSelector();
      }
      else {
        // Others: @@system_path@@/log
        ourLogPath = getSystemPath() + File.separatorChar + LOG_DIRECTORY;
      }
    }

    return ourLogPath;
  }

  @NotNull
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

  @Nullable
  public static String getOriginalWorkingDir() {
    return System.getProperty(PROPERTY_ORIGINAL_WORKING_DIR);
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
  @Nullable
  @NonNls
  private static String extractRoot(URL resourceURL, String resourcePath) {
    if (!(StringUtil.startsWithChar(resourcePath, '/') || StringUtil.startsWithChar(resourcePath, '\\'))) {
      //noinspection HardCodedStringLiteral,UseOfSystemOutOrSystemErr
      System.err.println("precondition failed: " + resourcePath);
      return null;
    }

    String resultPath = null;
    String protocol = resourceURL.getProtocol();
    if (URLUtil.FILE_PROTOCOL.equals(protocol)) {
      String path = resourceURL.getFile();
      String testPath = path.replace('\\', '/');
      String testResourcePath = resourcePath.replace('\\', '/');
      if (StringUtil.endsWithIgnoreCase(testPath, testResourcePath)) {
        resultPath = path.substring(0, path.length() - resourcePath.length());
      }
    }
    else if (URLUtil.JAR_PROTOCOL.equals(protocol)) {
      Pair<String, String> paths = URLUtil.splitJarUrl(resourceURL.getFile());
      if (paths != null) {
        resultPath = paths.first;
      }
    }

    if (resultPath == null) {
      //noinspection HardCodedStringLiteral,UseOfSystemOutOrSystemErr
      System.err.println("cannot extract: " + resultPath + " from " + resourceURL);
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
      try {
        InputStream fis = new BufferedInputStream(new FileInputStream(propFile));
        try {
          final PropertyResourceBundle bundle = new PropertyResourceBundle(fis);
          final Enumeration keys = bundle.getKeys();
          String home = (String)bundle.handleGetObject("idea.home");
          if (home != null && ourHomePath == null) {
            ourHomePath = getAbsolutePath(substituteVars(home));
          }
          final Properties sysProperties = System.getProperties();
          while (keys.hasMoreElements()) {
            String key = (String)keys.nextElement();
            if (sysProperties.getProperty(key, null) == null) { // load the property from the property file only if it is not defined yet
              final String value = substituteVars(bundle.getString(key));
              sysProperties.setProperty(key, value);
            }
          }
        }
        finally{
          fis.close();
        }
      }
      catch (IOException e) {
        //noinspection HardCodedStringLiteral,UseOfSystemOutOrSystemErr
        System.err.println("Problem reading from property file: " + propFile.getPath());
      }
    }
  }

  /** @deprecated use {@linkplain #substituteVars(String)} (to remove in IDEA 13) */
  @SuppressWarnings({"UnusedDeclaration", "ConstantConditions"})
  public static String substitueVars(String s) {
    return substituteVars(s);
  }

  @Nullable
  public static String substituteVars(String s) {
    final String ideaHomePath = getHomePath();
    return substituteVars(s, ideaHomePath);
  }

  @Nullable
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

  @TestOnly
  public static void cleanup() {
    ourPluginsPath = null;
  }

  @Nullable
  public static String getJarPathForClass(@NotNull Class aClass) {
    final String resourceRoot = getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    return resourceRoot != null ? new File(resourceRoot).getAbsolutePath() : null;
  }

  @NotNull
  public static Collection<String> getUtilClassPath() {
    final List<Class<?>> classes = Arrays.asList(
      PathManager.class,            // module 'util'
      NotNull.class,                // module 'annotations'
      SystemInfoRt.class,           // module 'util-rt'
      Document.class,               // jDOM
      Appender.class,               // log4j
      THashSet.class,               // trove4j
      PicoContainer.class,          // PicoContainer
      TypeMapper.class,             // JNA
      FileUtils.class,              // JNA (jna-utils)
      PatternMatcher.class          // OROMatcher
    );

    final Set<String> classPath = new HashSet<String>();
    for (Class<?> aClass : classes) {
      final String path = getJarPathForClass(aClass);
      if (path != null) {
        classPath.add(path);
      }
    }

    final String resourceRoot = getResourceRoot(PathManager.class, "/messages/CommonBundle.properties");  // platform-resources-en
    if (resourceRoot != null) {
      classPath.add(new File(resourceRoot).getAbsolutePath());
    }

    return Collections.unmodifiableCollection(classPath);
  }
}
