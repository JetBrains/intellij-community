/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.intellij.lang.annotations.Flow;
import org.iq80.snappy.Snappy;
import org.jdom.Document;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.util.SystemProperties.getUserHome;

public class PathManager {
  public static final String PROPERTIES_FILE = "idea.properties.file";
  public static final String PROPERTIES_FILE_NAME = "idea.properties";
  public static final String PROPERTY_HOME_PATH = "idea.home.path";
  public static final String PROPERTY_CONFIG_PATH = "idea.config.path";
  public static final String PROPERTY_SYSTEM_PATH = "idea.system.path";
  public static final String PROPERTY_SCRATCH_PATH = "idea.scratch.path";
  public static final String PROPERTY_PLUGINS_PATH = "idea.plugins.path";
  public static final String PROPERTY_LOG_PATH = "idea.log.path";
  public static final String PROPERTY_PATHS_SELECTOR = "idea.paths.selector";
  public static final String DEFAULT_OPTIONS_FILE_NAME = "other";

  private static final String PROPERTY_HOME = "idea.home";  // reduced variant of PROPERTY_HOME_PATH, now deprecated

  private static final String LIB_FOLDER = "lib";
  private static final String PLUGINS_FOLDER = "plugins";
  private static final String BIN_FOLDER = "bin";
  private static final String LOG_DIRECTORY = "log";
  private static final String CONFIG_FOLDER = "config";
  private static final String OPTIONS_FOLDER = "options";
  private static final String SYSTEM_FOLDER = "system";
  private static final String PATHS_SELECTOR = System.getProperty(PROPERTY_PATHS_SELECTOR);

  private static final Pattern PROPERTY_REF = Pattern.compile("\\$\\{(.+?)}");

  private static String ourHomePath;
  private static String ourConfigPath;
  private static String ourSystemPath;
  private static String ourScratchPath;
  private static String ourPluginsPath;
  private static String ourLogPath;

  // IDE installation paths

  @NotNull
  public static String getHomePath() {
    if (ourHomePath != null) return ourHomePath;

    String fromProperty = System.getProperty(PROPERTY_HOME_PATH, System.getProperty(PROPERTY_HOME));
    if (fromProperty != null) {
      ourHomePath = getAbsolutePath(fromProperty);
      if (!new File(ourHomePath).isDirectory()) {
        throw new RuntimeException("Invalid home path '" + ourHomePath + "'");
      }
    }
    else {
      ourHomePath = getHomePathFor(PathManager.class);
      if (ourHomePath == null) {
        String advice = SystemInfo.isMac ? "reinstall the software."
                                         : "make sure bin/idea.properties is present in the installation directory.";
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
    return new File(root, FileUtil.toSystemDependentName("bin/" + PROPERTIES_FILE_NAME)).exists() ||
           new File(root, FileUtil.toSystemDependentName("bin/" + getOSSpecificBinSubdir() + "/" + PROPERTIES_FILE_NAME)).exists() ||
           new File(root, FileUtil.toSystemDependentName("community/bin/" + PROPERTIES_FILE_NAME)).exists();
  }

  @NotNull
  public static String getBinPath() {
    return getHomePath() + File.separator + BIN_FOLDER;
  }

  private static String getOSSpecificBinSubdir() {
    if (SystemInfo.isWindows) return "win";
    if (SystemInfo.isMac) return "mac";
    return "linux";
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
  public static String getScratchPath() {
    if (ourScratchPath != null) return ourScratchPath;

    if (System.getProperty(PROPERTY_SCRATCH_PATH) != null) {
      ourScratchPath = getAbsolutePath(trimPathQuotes(System.getProperty(PROPERTY_SCRATCH_PATH)));
    }
    else {
      ourScratchPath = getConfigPath();
    }

    return ourScratchPath;
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
    else if (SystemInfo.isMac && PATHS_SELECTOR != null) {
      ourPluginsPath = getUserHome() + File.separator + "Library/Application Support" + File.separator + PATHS_SELECTOR;
    }
    else {
      ourPluginsPath = getConfigPath() + File.separatorChar + PLUGINS_FOLDER;
    }

    return ourPluginsPath;
  }

  @NotNull
  public static String getDefaultPluginPathFor(@NotNull String selector) {
    return platformPath(selector, "Library/Application Support", PLUGINS_FOLDER);
  }

  @Nullable
  public static String getCustomOptionsDirectory() {
    // do not use getConfigPath() here - as it may be not yet defined
    return PATHS_SELECTOR != null ? platformPath(PATHS_SELECTOR, "Library/Preferences", "") : null;
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
    else if (SystemInfo.isMac && PATHS_SELECTOR != null) {
      ourLogPath = getUserHome() + File.separator + "Library/Logs" + File.separator + PATHS_SELECTOR;
    }
    else {
      ourLogPath = getSystemPath() + File.separatorChar + LOG_DIRECTORY;
    }

    return ourLogPath;
  }

  @NotNull
  public static String getPluginTempPath() {
    return getSystemPath() + File.separator + PLUGINS_FOLDER;
  }

  // misc stuff

  /**
   * Attempts to detect classpath entry which contains given resource.
   */
  @Nullable
  public static String getResourceRoot(@NotNull Class context, String path) {
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
  private static String extractRoot(URL resourceURL, String resourcePath) {
    if (!(StringUtil.startsWithChar(resourcePath, '/') || StringUtil.startsWithChar(resourcePath, '\\'))) {
      log("precondition failed: " + resourcePath);
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
      log("cannot extract: " + resourcePath + " from " + resourceURL);
      return null;
    }

    if (SystemInfo.isWindows && resultPath.startsWith("/")) {
      resultPath = resultPath.substring(1);
    }
    resultPath = StringUtil.trimEnd(resultPath, File.separator);
    resultPath = URLUtil.unescapePercentSequences(resultPath);

    return resultPath;
  }

  public static void loadProperties() {
    String[] propFiles = {
      System.getProperty(PROPERTIES_FILE),
      getCustomPropertiesFile(),
      getUserHome() + "/" + PROPERTIES_FILE_NAME,
      getHomePath() + "/bin/" + PROPERTIES_FILE_NAME,
      getHomePath() + "/bin/" + getOSSpecificBinSubdir() + "/" + PROPERTIES_FILE_NAME,
      getHomePath() + "/community/bin/" + PROPERTIES_FILE_NAME};

    for (String path : propFiles) {
      if (path != null) {
        File propFile = new File(path);
        if (propFile.exists()) {
          try {
            Reader fis = new BufferedReader(new FileReader(propFile));
            try {
              Map<String, String> properties = FileUtil.loadProperties(fis);

              Properties sysProperties = System.getProperties();
              for (String key : properties.keySet()) {
                if (PROPERTY_HOME_PATH.equals(key) || PROPERTY_HOME.equals(key)) {
                  log(propFile.getPath() + ": '" + PROPERTY_HOME_PATH + "' and '" + PROPERTY_HOME + "' properties cannot be redefined");
                }
                else if (sysProperties.getProperty(key, null) != null) {
                  log(propFile.getPath() + ": '" + key + "' already defined");
                }
                else {
                  String value = substituteVars(properties.get(key));
                  sysProperties.setProperty(key, value);
                }
              }
            }
            finally {
              fis.close();
            }
          }
          catch (IOException e) {
            log("Problem reading from property file: " + propFile.getPath());
          }
        }
      }
    }
  }

  private static String getCustomPropertiesFile() {
    String configPath = getCustomOptionsDirectory();
    return configPath != null ? configPath + File.separator + PROPERTIES_FILE_NAME : null;
  }

  @Contract("null -> null")
  public static String substituteVars(String s) {
    return substituteVars(s, getHomePath());
  }

  @Contract("null, _ -> null")
  public static String substituteVars(String s, String ideaHomePath) {
    if (s == null) return null;

    if (s.startsWith("..")) {
      s = ideaHomePath + File.separatorChar + BIN_FOLDER + File.separatorChar + s;
    }

    Matcher m = PROPERTY_REF.matcher(s);
    while (m.find()) {
      String key = m.group(1);
      String value = System.getProperty(key);

      if (value == null) {
        if (PROPERTY_HOME_PATH.equals(key) || PROPERTY_HOME.equals(key)) {
          value = ideaHomePath;
        }
        else if (PROPERTY_CONFIG_PATH.equals(key)) {
          value = getConfigPath();
        }
        else if (PROPERTY_SYSTEM_PATH.equals(key)) {
          value = getSystemPath();
        }
      }

      if (value == null) {
        log("Unknown property: " + key);
        value = "";
      }

      s = StringUtil.replace(s, m.group(), value);
      m = PROPERTY_REF.matcher(s);
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
      Flow.class,                   // module 'annotations'
      SystemInfoRt.class,           // module 'util-rt'
      Document.class,               // jDOM
      Appender.class,               // log4j
      THashSet.class,               // trove4j
      PicoContainer.class,          // PicoContainer
      TypeMapper.class,             // JNA
      FileUtils.class,              // JNA (jna-platform)
      PatternMatcher.class,          // OROMatcher
      Snappy.class                   // Snappy
    };

    final Set<String> classPath = new HashSet<String>();
    for (Class<?> aClass : classes) {
      final String path = getJarPathForClass(aClass);
      if (path != null) {
        classPath.add(path);
      }
    }

    final String annotationsRoot = getJarPathForClass(Flow.class);
    if (annotationsRoot != null && !annotationsRoot.endsWith(".jar")) {
      // We're running IDEA built from sources. Flow.class is under annotations-common, and NotNull.class is under annotations. Add both
      // roots to classpath.
      final File notNullRoot = new File(new File(annotationsRoot).getParentFile(), "annotations");
      if (notNullRoot.exists()) {
        classPath.add(notNullRoot.getAbsolutePath());
      }
    }

    final String resourceRoot = getResourceRoot(PathManager.class, "/messages/CommonBundle.properties");  // platform-resources-en
    if (resourceRoot != null) {
      classPath.add(new File(resourceRoot).getAbsolutePath());
    }

    return Collections.unmodifiableCollection(classPath);
  }

  // helpers

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void log(String x) {
    System.err.println(x);
  }

  private static String getAbsolutePath(String path) {
    path = FileUtil.expandUserHome(path);
    return FileUtil.toCanonicalPath(new File(path).getAbsolutePath());
  }

  private static String trimPathQuotes(String path) {
    if (path != null && path.length() >= 3 && StringUtil.startsWithChar(path, '\"') && StringUtil.endsWithChar(path, '\"')) {
      path = path.substring(1, path.length() - 1);
    }
    return path;
  }

  // todo[r.sh] XDG directories, Windows folders
  // http://standards.freedesktop.org/basedir-spec/basedir-spec-latest.html
  // http://www.microsoft.com/security/portal/mmpc/shared/variables.aspx
  private static String platformPath(@NotNull String selector, @Nullable String macPart, @NotNull String fallback) {
    return platformPath(selector, macPart, null, null, null, fallback);
  }

  private static String platformPath(@NotNull String selector,
                                     @Nullable String macPart,
                                     @Nullable String winVar,
                                     @Nullable String xdgVar,
                                     @Nullable String xdgDir,
                                     @NotNull String fallback) {
    if (macPart != null && SystemInfo.isMac) {
      return getUserHome() + File.separator + macPart + File.separator + selector;
    }

    if (winVar != null && SystemInfo.isWindows) {
      String dir = System.getenv(winVar);
      if (dir != null) {
        return dir + File.separator + selector;
      }
    }

    if (xdgVar != null && xdgDir != null && SystemInfo.hasXdgOpen()) {
      String dir = System.getenv(xdgVar);
      if (dir == null) dir = getUserHome() + File.separator + xdgDir;
      return dir + File.separator + selector;
    }

    return getUserHome() + File.separator + "." + selector + (!fallback.isEmpty() ? File.separator + fallback : "");
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
}
