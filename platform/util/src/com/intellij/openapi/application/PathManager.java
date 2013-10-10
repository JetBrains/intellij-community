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
import com.intellij.util.io.URLUtil;
import com.sun.jna.TypeMapper;
import com.sun.jna.platform.FileUtils;
import gnu.trove.THashSet;
import org.apache.log4j.Appender;
import org.apache.oro.text.regex.PatternMatcher;
import org.jdom.Document;
import org.jetbrains.annotations.*;
import org.picocontainer.PicoContainer;

import java.io.*;
import java.net.URL;
import java.util.*;

import static com.intellij.util.SystemProperties.getUserHome;

public class PathManager {
  @NonNls public static final String PROPERTIES_FILE = "idea.properties.file";
  @NonNls public static final String PROPERTY_SYSTEM_PATH = "idea.system.path";
  @NonNls public static final String PROPERTY_CONFIG_PATH = "idea.config.path";
  @NonNls public static final String PROPERTY_PLUGINS_PATH = "idea.plugins.path";
  @NonNls public static final String PROPERTY_HOME_PATH = "idea.home.path";
  @NonNls public static final String PROPERTY_LOG_PATH = "idea.log.path";
  @NonNls public static final String PROPERTY_PATHS_SELECTOR = "idea.paths.selector";
  @NonNls public static final String PROPERTY_ORIGINAL_WORKING_DIR = "original.working.dir";
  @NonNls public static final String DEFAULT_OPTIONS_FILE_NAME = "other";

  @NonNls private static final String LIB_FOLDER = "lib";
  @NonNls private static final String PLUGINS_FOLDER = "plugins";
  @NonNls private static final String BIN_FOLDER = "bin";
  @NonNls private static final String LOG_DIRECTORY = "log";
  @NonNls private static final String CONFIG_FOLDER = "config";
  @NonNls private static final String OPTIONS_FOLDER = "options";
  @NonNls private static final String SYSTEM_FOLDER = "system";
  @NonNls private static final String PATHS_SELECTOR = System.getProperty(PROPERTY_PATHS_SELECTOR);

  @NonNls private static String ourHomePath;
  @NonNls private static String ourSystemPath;
  @NonNls private static String ourConfigPath;
  @NonNls private static String ourPluginsPath;
  @NonNls private static String ourLogPath;

  // IDE installation paths

  @NotNull
  public static String getHomePath() {
    if (ourHomePath != null) return ourHomePath;

    String fromProperty = System.getProperty(PROPERTY_HOME_PATH);
    if (fromProperty != null) {
      ourHomePath = getAbsolutePath(fromProperty);
      if (!new File(ourHomePath).isDirectory()) {
        throw new RuntimeException("Invalid home path '" + ourHomePath + "'");
      }
    }
    else {
      ourHomePath = getHomePathFor(PathManager.class);
      if (ourHomePath == null) {
        String advice = SystemInfo.isMac ? "reinstall the software." : "make sure bin/idea.properties is present in the installation directory.";
        throw new RuntimeException("Could not find installation home path. Please " + advice);
      }
    }

    if (SystemInfo.isWindows) {
      try {
        ourHomePath = new File(ourHomePath).getCanonicalPath();
      }
      catch (IOException ignored) { }
    }

    return ourHomePath;
  }

  @Nullable
  public static String getHomePathFor(@NotNull Class aClass) {
    String rootPath = getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    if (rootPath == null) return null;

    File root = new File(rootPath).getAbsoluteFile();
    do {
      String parent = root.getParent();
      if (parent == null) return null;
      root = new File(parent).getAbsoluteFile();  // one step back to get folder
    }
    while (!isIdeaHome(root));
    return root.getAbsolutePath();
  }

  private static boolean isIdeaHome(final File root) {
    return new File(root, FileUtil.toSystemDependentName("bin/idea.properties")).exists() ||
           new File(root, FileUtil.toSystemDependentName("community/bin/idea.properties")).exists() ||
           new File(root, FileUtil.toSystemDependentName("Contents/Info.plist")).exists();  // MacOS bundle doesn't include idea.properties
  }

  @NotNull
  public static String getBinPath() {
    return getHomePath() + File.separator + BIN_FOLDER;
  }

  @NotNull
  public static String getLibPath() {
    return getHomePath() + File.separator + LIB_FOLDER;
  }

  @SuppressWarnings("MethodNamesDifferingOnlyByCase")
  @NotNull
  public static String getPreInstalledPluginsPath() {
    return getHomePath() + File.separatorChar + PLUGINS_FOLDER;
  }

  // config paths

  @Nullable
  public static String getPathsSelector() {
    return PATHS_SELECTOR;
  }

  @NotNull
  public static String getConfigPath() {
    if (ourConfigPath != null) return ourConfigPath;

    if (System.getProperty(PROPERTY_CONFIG_PATH) != null) {
      ourConfigPath = getAbsolutePath(trimPathQuotes(System.getProperty(PROPERTY_CONFIG_PATH)));
    }
    else if (PATHS_SELECTOR != null) {
      ourConfigPath = getDefaultConfigPathFor(PATHS_SELECTOR);
    }
    else {
      ourConfigPath = getHomePath() + File.separator + CONFIG_FOLDER;
    }

    return ourConfigPath;
  }

  @NotNull
  public static String getDefaultConfigPathFor(@NotNull String selector) {
    return platformPath(selector, "Library/Preferences", CONFIG_FOLDER);
  }

  public static void ensureConfigFolderExists() {
    checkAndCreate(getConfigPath(), true);
  }

  @NotNull
  public static String getOptionsPath() {
    return getConfigPath() + File.separator + OPTIONS_FOLDER;
  }

  @NotNull
  public static File getOptionsFile(@NotNull String fileName) {
    return new File(getOptionsPath(), fileName + ".xml");
  }

  @NotNull
  public static File getOptionsFile(@NotNull NamedJDOMExternalizable externalizable) {
    return getOptionsFile(externalizable.getExternalFileName());
  }

  @NotNull
  public static String getPluginsPath() {
    if (ourPluginsPath != null) return ourPluginsPath;

    if (System.getProperty(PROPERTY_PLUGINS_PATH) != null) {
      ourPluginsPath = getAbsolutePath(trimPathQuotes(System.getProperty(PROPERTY_PLUGINS_PATH)));
    }
    else if (PATHS_SELECTOR != null) {
      ourPluginsPath = platformPath(PATHS_SELECTOR, "Library/Application Support", CONFIG_FOLDER + File.separatorChar + PLUGINS_FOLDER);
    }
    else {
      ourPluginsPath = getConfigPath() + File.separatorChar + PLUGINS_FOLDER;
    }

    return ourPluginsPath;
  }

  // runtime paths

  @NotNull
  public static String getSystemPath() {
    if (ourSystemPath != null) return ourSystemPath;

    if (System.getProperty(PROPERTY_SYSTEM_PATH) != null) {
      ourSystemPath = getAbsolutePath(trimPathQuotes(System.getProperty(PROPERTY_SYSTEM_PATH)));
    }
    else if (PATHS_SELECTOR != null) {
      ourSystemPath = platformPath(PATHS_SELECTOR, "Library/Caches", SYSTEM_FOLDER);
    }
    else {
      ourSystemPath = getHomePath() + File.separator + SYSTEM_FOLDER;
    }

    checkAndCreate(ourSystemPath, true);
    return ourSystemPath;
  }

  @NotNull
  public static String getTempPath() {
    return getSystemPath() + File.separator + "tmp";
  }

  @NotNull
  public static File getIndexRoot() {
    String indexRoot = System.getProperty("index_root_path", getSystemPath() + "/index");
    checkAndCreate(indexRoot, true);
    return new File(indexRoot);
  }

  @NotNull
  public static String getLogPath() {
    if (ourLogPath != null) return ourLogPath;

    if (System.getProperty(PROPERTY_LOG_PATH) != null) {
      ourLogPath = getAbsolutePath(trimPathQuotes(System.getProperty(PROPERTY_LOG_PATH)));
    }
    else if (PATHS_SELECTOR != null) {
      ourLogPath = platformPath(PATHS_SELECTOR, "Library/Logs", SYSTEM_FOLDER + File.separatorChar + LOG_DIRECTORY);
    }
    else {
      ourLogPath = getSystemPath() + File.separatorChar + LOG_DIRECTORY;
    }

    return ourLogPath;
  }

  @NotNull
  public static String getPluginTempPath () {
    return getSystemPath() + File.separator + PLUGINS_FOLDER;
  }

  // misc stuff

  @Nullable
  public static String getOriginalWorkingDir() {
    return System.getProperty(PROPERTY_ORIGINAL_WORKING_DIR);
  }

  /**
   * Attempts to detect classpath entry which contains given resource.
   */
  @Nullable
  public static String getResourceRoot(@NotNull Class context, @NonNls String path) {
    URL url = context.getResource(path);
    if (url == null) {
      url = ClassLoader.getSystemResource(path.substring(1));
    }
    return url != null ? extractRoot(url, path) : null;
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
      System.err.println("cannot extract: " + resourcePath + " from " + resourceURL);
      return null;
    }

    resultPath = StringUtil.trimEnd(resultPath, File.separator);
    resultPath = URLUtil.unescapePercentSequences(resultPath);

    return resultPath;
  }

  public static void loadProperties() {
    File propFile = FileUtil.findFirstThatExist(
      System.getProperty(PROPERTIES_FILE),
      getUserHome() + "/idea.properties",
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

  @Contract("null -> null")
  public static String substituteVars(String s) {
    final String ideaHomePath = getHomePath();
    return substituteVars(s, ideaHomePath);
  }

  @Contract("null, _ -> null")
  public static String substituteVars(String s, String ideaHomePath) {
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

  @NotNull
  public static File findFileInLibDirectory(@NotNull String relativePath) {
    File file = new File(getLibPath() + File.separator + relativePath);
    return file.exists() ? file : new File(getHomePath(), "community" + File.separator + "lib" + File.separator + relativePath);
  }

  @Nullable
  public static String getJarPathForClass(@NotNull Class aClass) {
    String resourceRoot = getResourceRoot(aClass, "/" + aClass.getName().replace('.', '/') + ".class");
    return resourceRoot != null ? new File(resourceRoot).getAbsolutePath() : null;
  }

  @NotNull
  public static Collection<String> getUtilClassPath() {
    final Class<?>[] classes = {
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
    };

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

  // helpers

  private static String getAbsolutePath(String path) {
    if (path.startsWith("~/") || path.startsWith("~\\")) {
      path = getUserHome() + path.substring(1);
    }
    return new File(path).getAbsolutePath();
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

  // todo[r.sh] XDG directories, Windows folders
  private static String platformPath(String selector, String macDir, String fallback) {
    if (SystemInfo.isMac) {
      return getUserHome() + File.separator + macDir + File.separator + selector;
    }
    else {
      return getUserHome() + File.separator + "." + selector + File.separator + fallback;
    }
  }

  private static boolean checkAndCreate(String path, boolean createIfNotExists) {
    if (createIfNotExists) {
      File file = new File(path);
      if (!file.exists()) {
        return file.mkdirs();
      }
    }
    return false;
  }

  // outdated stuff

  /** @deprecated use {@link #getPluginsPath()} (to remove in IDEA 14) */
  @SuppressWarnings("UnusedDeclaration") @NonNls public static final String PLUGINS_DIRECTORY = PLUGINS_FOLDER;

  /** @deprecated use {@link #getPreInstalledPluginsPath()} (to remove in IDEA 14) */
  @SuppressWarnings({"UnusedDeclaration", "MethodNamesDifferingOnlyByCase", "SpellCheckingInspection"})
  public static String getPreinstalledPluginsPath() {
    return getPreInstalledPluginsPath();
  }

  /** @deprecated use {@link #getConfigPath()} (to remove in IDEA 14) */
  @SuppressWarnings("UnusedDeclaration")
  public static String getConfigPath(boolean createIfNotExists) {
    ensureConfigFolderExists();
    return ourConfigPath;
  }

  /** @deprecated use {@link #ensureConfigFolderExists()} (to remove in IDEA 14) */
  @SuppressWarnings("UnusedDeclaration")
  public static boolean ensureConfigFolderExists(boolean createIfNotExists) {
    return checkAndCreate(getConfigPath(), createIfNotExists);
  }

  /** @deprecated use {@link #getOptionsPath()} (to remove in IDEA 14) */
  @SuppressWarnings("UnusedDeclaration")
  public static String getOptionsPathWithoutDialog() {
    return getOptionsPath();
  }

  /** @deprecated use {@link #getOptionsFile(String)} (to remove in IDEA 14) */
  @SuppressWarnings("UnusedDeclaration")
  public static File getDefaultOptionsFile() {
    return new File(getOptionsPath(), DEFAULT_OPTIONS_FILE_NAME + ".xml");
  }
}
