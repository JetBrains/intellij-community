// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.PropertiesUtil;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.util.text.Strings;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.FList;
import com.intellij.util.io.URLUtil;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PathManager {
  public static final String PROPERTIES_FILE = "idea.properties.file";
  public static final String PROPERTIES_FILE_NAME = "idea.properties";
  public static final String PROPERTY_HOME_PATH = "idea.home.path";
  public static final String PROPERTY_CONFIG_PATH = "idea.config.path";
  public static final String PROPERTY_SYSTEM_PATH = "idea.system.path";
  public static final String PROPERTY_SCRATCH_PATH = "idea.scratch.path";
  public static final String PROPERTY_PLUGINS_PATH = "idea.plugins.path";
  public static final String PROPERTY_LOG_PATH = "idea.log.path";
  public static final String PROPERTY_LOG_CONFIG_FILE = "idea.log.config.file";
  public static final String PROPERTY_PATHS_SELECTOR = "idea.paths.selector";

  public static final String OPTIONS_DIRECTORY = "options";
  public static final String DEFAULT_EXT = ".xml";
  public static final String DEFAULT_OPTIONS_FILE = "other" + DEFAULT_EXT;

  private static final String KOTLIN_IDE_IML_RELATIVE_PATH = "kotlin/idea/kotlin.idea.iml";
  private static final String INTELLIJ_SUB_REPO_NAME = "intellij";

  private static final String PROPERTY_HOME = "idea.home";  // reduced variant of PROPERTY_HOME_PATH, now deprecated
  private static final String PROPERTY_VENDOR_NAME = "idea.vendor.name";

  private static final String LIB_DIRECTORY = "lib";
  private static final String PLUGINS_DIRECTORY = "plugins";
  private static final String BIN_DIRECTORY = "bin";
  private static final String LOG_DIRECTORY = "log";
  private static final String CONFIG_DIRECTORY = "config";
  private static final String SYSTEM_DIRECTORY = "system";
  private static final String PATHS_SELECTOR = System.getProperty(PROPERTY_PATHS_SELECTOR);

  private static class Lazy {
    private static final Pattern PROPERTY_REF = Pattern.compile("\\$\\{(.+?)}");
  }

  private static volatile String ourHomePath;
  private static volatile List<Path> ourBinDirectories;
  private static Path ourCommonDataPath;
  private static String ourConfigPath;
  private static String ourSystemPath;
  private static String ourScratchPath;
  private static String ourPluginsPath;
  private static String ourLogPath;

  // IDE installation paths

  public static @NotNull String getHomePath() {
    return getHomePath(true);
  }

  /**
   * @param insideIde {@code true} if the calling code works inside IDE; {@code false} otherwise (e.g. in a build process or a script)
   */
  @Contract("true -> !null")
  public static String getHomePath(boolean insideIde) {
    String result = ourHomePath;
    if (result != null) return result;

    //noinspection SynchronizeOnThis
    synchronized (PathManager.class) {
      result = ourHomePath;
      if (result != null) return result;

      String explicit = getExplicitPath(PROPERTY_HOME_PATH);
      if (explicit == null) explicit = getExplicitPath(PROPERTY_HOME);
      if (explicit != null) {
        result = explicit;
        if (!Files.isDirectory(Paths.get(result))) {
          ourHomePath = result;
          throw new RuntimeException("Invalid home path '" + result + "'");
        }
      }
      else if (insideIde) {
        result = getHomePathFor(PathManager.class);
        if (result == null) {
          String advice =
            SystemInfoRt.isMac ? "reinstall the software." : "make sure bin/idea.properties is present in the installation directory.";
          throw new RuntimeException("Could not find installation home path. Please " + advice);
        }
      }

      if (result != null && SystemInfoRt.isWindows) {
        try {
          result = Paths.get(result).toRealPath(LinkOption.NOFOLLOW_LINKS).toString();
        }
        catch (IOException ignored) { }
      }

      // set before ourHomePath because getBinDirectories() rely on fact that if getHomePath(true) returns something, then ourBinDirectories is already computed
      ourBinDirectories = result == null ?  Collections.emptyList() : getBinDirectories(Paths.get(result));
      ourHomePath = result;
    }

    return result;
  }

  private static List<Path> getBinDirectories() {
    List<Path> result = ourBinDirectories;
    if (result == null) {
      getHomePath(true);
      result = ourBinDirectories;
    }
    return result;
  }

  public static boolean isUnderHomeDirectory(@NotNull String path) {
    Path home = Paths.get(getHomePath()), target = Paths.get(path);
    try {
      home = home.toRealPath();
      target = target.toRealPath();
    }
    catch (IOException ignored) { }
    return target.startsWith(home);
  }

  public static @Nullable String getHomePathFor(@NotNull Class<?> aClass) {
    String rootPath = getResourceRoot(aClass, '/' + aClass.getName().replace('.', '/') + ".class");
    if (rootPath == null) return null;

    Path root = Paths.get(rootPath).toAbsolutePath();
    do {
      root = root.getParent();
    } while (root != null && !isIdeaHome(root) && !isKotlinIdeRepoHome(root));
    if (root != null && isKotlinIdeRepoHome(root)) {
      root = root.resolve(INTELLIJ_SUB_REPO_NAME);
    }
    return root != null ? root.toString() : null;
  }

  /**
   * Checks whether it's intellij + kotlin kotlin-ide repo home.
   * <p></p>
   * This is temp util method and it's supposed to be removed when kotlin-20202 experiment is over
   */
  private static boolean isKotlinIdeRepoHome(@NotNull Path path) {
    return Files.isDirectory(path) &&
           Files.isRegularFile(path.resolve(KOTLIN_IDE_IML_RELATIVE_PATH)) &&
           Files.isDirectory(path.resolve(INTELLIJ_SUB_REPO_NAME)) &&
           isIdeaHome(path.resolve(INTELLIJ_SUB_REPO_NAME));
  }

  private static boolean isIdeaHome(Path root) {
    for (Path binDir : getBinDirectories(root)) {
      if (Files.isRegularFile(binDir.resolve(PROPERTIES_FILE_NAME))) {
        return true;
      }
    }
    return false;
  }

  private static List<Path> getBinDirectories(Path root) {
    List<Path> binDirs = new ArrayList<>();

    String[] subDirs = {BIN_DIRECTORY, "community/bin", "ultimate/community/bin"};
    String osSuffix = SystemInfoRt.isWindows ? "win" : SystemInfoRt.isMac ? "mac" : "linux";

    for (String subDir : subDirs) {
      Path dir = root.resolve(subDir);
      if (Files.isDirectory(dir)) {
        binDirs.add(dir);
        dir = dir.resolve(osSuffix);
        if (Files.isDirectory(dir)) {
          binDirs.add(dir);
        }
      }
    }

    return binDirs;
  }

  /**
   * Bin path may be not what you want when developing an IDE. Consider using {@link #findBinFile(String)} if applicable.
   */
  public static @NotNull String getBinPath() {
    return getHomePath() + '/' + BIN_DIRECTORY;
  }

  /**
   * Looks for a file in all possible bin directories.
   *
   * @return first that exists, or {@code null} if nothing found.
   * @see #findBinFileWithException(String)
   */
  public static @Nullable Path findBinFile(@NotNull String fileName) {
    for (Path binDir : getBinDirectories()) {
      Path candidate = binDir.resolve(fileName);
      if (Files.isRegularFile(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  /**
   * Looks for a file in all possible bin directories.
   *
   * @return first that exists.
   * @throws FileNotFoundException if nothing found.
   * @see #findBinFile(String)
   */
  public static @NotNull Path findBinFileWithException(@NotNull String fileName) throws FileNotFoundException {
    Path file = findBinFile(fileName);
    if (file != null) {
      return file;
    }

    StringBuilder message = new StringBuilder();
    message.append('\'').append(fileName).append("' not found in directories:");
    for (Path directory : getBinDirectories()) {
      message.append('\n').append(directory);
    }
    throw new FileNotFoundException(message.toString());
  }

  public static @NotNull String getLibPath() {
    return getHomePath() + '/' + LIB_DIRECTORY;
  }

  public static @NotNull String getPreInstalledPluginsPath() {
    return getHomePath() + '/' + PLUGINS_DIRECTORY;
  }

  // config paths

  public static synchronized @NotNull Path getCommonDataPath() {
    if (ourCommonDataPath == null) {
      Path path = Paths.get(platformPath("", "Application Support", "", "APPDATA", "", "XDG_DATA_HOME", ".local/share", ""));
      if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
        try {
          Files.createDirectories(path);
        }
        catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
      ourCommonDataPath = path;
    }
    return ourCommonDataPath;
  }

  public static @Nullable String getPathsSelector() {
    return PATHS_SELECTOR;
  }

  public static @NotNull Path getConfigDir() {
    return Paths.get(getConfigPath());
  }

  public static @NotNull String getConfigPath() {
    if (ourConfigPath != null) return ourConfigPath;

    String explicit = getExplicitPath(PROPERTY_CONFIG_PATH);
    if (explicit != null) {
      ourConfigPath = explicit;
    }
    else if (PATHS_SELECTOR != null) {
      ourConfigPath = getDefaultConfigPathFor(PATHS_SELECTOR);
    }
    else {
      ourConfigPath = getHomePath() + '/' + CONFIG_DIRECTORY;
    }

    return ourConfigPath;
  }

  public static @NotNull String getScratchPath() {
    if (ourScratchPath != null) return ourScratchPath;

    String explicit = getExplicitPath(PROPERTY_SCRATCH_PATH);
    if (explicit != null) {
      ourScratchPath = explicit;
    }
    else {
      ourScratchPath = getConfigPath();
    }

    return ourScratchPath;
  }

  public static @NotNull String getDefaultConfigPathFor(@NotNull String selector) {
    return platformPath(selector, "Application Support", "", "APPDATA", "", "XDG_CONFIG_HOME", ".config", "");
  }

  public static @NotNull String getOptionsPath() {
    return getConfigPath() + '/' + OPTIONS_DIRECTORY;
  }

  public static @NotNull File getOptionsFile(@NotNull String fileName) {
    return Paths.get(getOptionsPath(), fileName + DEFAULT_EXT).toFile();
  }

  public static @NotNull String getPluginsPath() {
    if (ourPluginsPath != null) return ourPluginsPath;

    String explicit = getExplicitPath(PROPERTY_PLUGINS_PATH);
    if (explicit != null) {
      ourPluginsPath = explicit;
    }
    else if (PATHS_SELECTOR != null && System.getProperty(PROPERTY_CONFIG_PATH) == null) {
      ourPluginsPath = getDefaultPluginPathFor(PATHS_SELECTOR);
    }
    else {
      ourPluginsPath = getConfigPath() + '/' + PLUGINS_DIRECTORY;
    }

    return ourPluginsPath;
  }

  public static @NotNull String getDefaultPluginPathFor(@NotNull String selector) {
    return platformPath(selector, "Application Support", PLUGINS_DIRECTORY, "APPDATA", PLUGINS_DIRECTORY, "XDG_DATA_HOME", ".local/share", "");
  }

  public static @Nullable String getCustomOptionsDirectory() {
    // do not use getConfigPath() here - as it may be not yet defined
    return PATHS_SELECTOR != null ? getDefaultConfigPathFor(PATHS_SELECTOR) : null;
  }

  // runtime paths

  public static @NotNull String getSystemPath() {
    if (ourSystemPath != null) return ourSystemPath;

    String explicit = getExplicitPath(PROPERTY_SYSTEM_PATH);
    if (explicit != null) {
      ourSystemPath = explicit;
    }
    else if (PATHS_SELECTOR != null) {
      ourSystemPath = getDefaultSystemPathFor(PATHS_SELECTOR);
    }
    else {
      ourSystemPath = getHomePath() + '/' + SYSTEM_DIRECTORY;
    }

    return ourSystemPath;
  }

  public static @NotNull String getDefaultSystemPathFor(@NotNull String selector) {
    return platformPath(selector, "Caches", "", "LOCALAPPDATA", "", "XDG_CACHE_HOME", ".cache", "");
  }

  public static @NotNull String getTempPath() {
    return getSystemPath() + "/tmp";
  }

  public static @NotNull File getIndexRoot() {
    String indexRootPath = getExplicitPath("index_root_path");
    if (indexRootPath == null) indexRootPath = getSystemPath() + "/index";
    return Paths.get(indexRootPath).toFile();
  }

  public static @NotNull String getLogPath() {
    if (ourLogPath != null) return ourLogPath;

    String explicit = getExplicitPath(PROPERTY_LOG_PATH);
    if (explicit != null) {
      ourLogPath = explicit;
    }
    else if (PATHS_SELECTOR != null && System.getProperty(PROPERTY_SYSTEM_PATH) == null) {
      ourLogPath = platformPath(PATHS_SELECTOR, "Logs", "", "LOCALAPPDATA", LOG_DIRECTORY, "XDG_CACHE_HOME", ".cache", LOG_DIRECTORY);
    }
    else {
      ourLogPath = getSystemPath() + '/' + LOG_DIRECTORY;
    }

    return ourLogPath;
  }

  public static @NotNull String getPluginTempPath() {
    return getSystemPath() + '/' + PLUGINS_DIRECTORY;
  }

  // misc stuff

  /**
   * Attempts to detect classpath entry containing given resource.
   */
  public static @Nullable String getResourceRoot(@NotNull Class<?> context, @NotNull String path) {
    URL url = context.getResource(path);
    if (url == null) {
      url = ClassLoader.getSystemResource(path.substring(1));
    }
    return url != null ? extractRoot(url, path) : null;
  }

  /**
   * Attempts to detect classpath entry containing given resource.
   */
  public static @Nullable String getResourceRoot(@NotNull ClassLoader cl, @NotNull String resourcePath) {
    URL url = cl.getResource(resourcePath);
    return url != null ? extractRoot(url, resourcePath) : null;
  }

  /**
   * Attempts to extract classpath entry part from passed URL.
   */
  private static @Nullable String extractRoot(URL resourceURL, String resourcePath) {
    if (resourcePath.length() == 0 || resourcePath.charAt(0) != '/' && resourcePath.charAt(0) != '\\') {
      log("precondition failed: " + resourcePath);
      return null;
    }

    String resultPath = null;
    String protocol = resourceURL.getProtocol();
    if (URLUtil.FILE_PROTOCOL.equals(protocol)) {
      String path = URLUtil.urlToFile(resourceURL).getPath();
      String testPath = path.replace('\\', '/');
      String testResourcePath = resourcePath.replace('\\', '/');
      if (StringUtilRt.endsWithIgnoreCase(testPath, testResourcePath)) {
        resultPath = path.substring(0, path.length() - resourcePath.length());
      }
    }
    else if (URLUtil.JAR_PROTOCOL.equals(protocol)) {
      Pair<String, String> paths = URLUtil.splitJarUrl(resourceURL.getFile());
      if (paths != null && paths.first != null) {
        resultPath = paths.first;
      }
    }
    else if (URLUtil.JRT_PROTOCOL.equals(protocol)) {
      return null;
    }

    if (resultPath == null) {
      log("cannot extract '" + resourcePath + "' from '" + resourceURL + "'");
      return null;
    }

    return Paths.get(resultPath).normalize().toString();
  }

  public static void loadProperties() {
    Set<String> paths = new LinkedHashSet<>();
    paths.add(System.getProperty(PROPERTIES_FILE));
    paths.add(getCustomPropertiesFile());
    paths.add(SystemProperties.getUserHome() + '/' + PROPERTIES_FILE_NAME);
    for (Path binDir : getBinDirectories()) {
      paths.add(binDir.resolve(PROPERTIES_FILE_NAME).toString());
    }

    Properties sysProperties = System.getProperties();
    for (String path : paths) {
      Path file = path != null ? Paths.get(path) : null;
      if (file != null && Files.exists(file)) {
        try (Reader reader = Files.newBufferedReader(file)) {
          Map<String, String> properties = PropertiesUtil.loadProperties(reader);
          for (Map.Entry<String, String> entry : properties.entrySet()) {
            String key = entry.getKey();
            if (PROPERTY_HOME_PATH.equals(key) || PROPERTY_HOME.equals(key)) {
              log(path + ": '" + key + "' cannot be redefined");
            }
            else if (!sysProperties.containsKey(key)) {
              sysProperties.setProperty(key, substituteVars(entry.getValue()));
            }
          }
        }
        catch (IOException e) {
          log("Can't read property file '" + path + "': " + e.getMessage());
        }
      }
    }
  }

  private static String getCustomPropertiesFile() {
    String configPath = getCustomOptionsDirectory();
    return configPath != null ? configPath + '/' + PROPERTIES_FILE_NAME : null;
  }

  @Contract("null -> null")
  public static String substituteVars(String s) {
    return substituteVars(s, getHomePath());
  }

  @Contract("null, _ -> null")
  public static String substituteVars(String s, @NotNull String ideaHomePath) {
    if (s == null) return null;

    if (s.startsWith("..")) {
      s = ideaHomePath + '/' + BIN_DIRECTORY + '/' + s;
    }

    Matcher m = Lazy.PROPERTY_REF.matcher(s);
    while (m.find()) {
      String key = m.group(1);
      String value = System.getProperty(key);

      if (value == null) {
        switch (key) {
          case PROPERTY_HOME_PATH:
          case PROPERTY_HOME:
            value = ideaHomePath;
            break;
          case PROPERTY_CONFIG_PATH:
            value = getConfigPath();
            break;
          case PROPERTY_SYSTEM_PATH:
            value = getSystemPath();
            break;
        }
      }

      if (value == null) {
        log("Unknown property: " + key);
        value = "";
      }

      s = s.replace(m.group(), value);
      m = Lazy.PROPERTY_REF.matcher(s);
    }

    return s;
  }

  public static @NotNull File findFileInLibDirectory(@NotNull String relativePath) {
    Path file = Paths.get(getLibPath(), relativePath);
    if (!Files.exists(file)) file = Paths.get(getHomePath(), "community/lib/" + relativePath);
    return file.toFile();
  }

  /**
   * @return path to 'community' project home irrespective of current project
   */
  public static @NotNull String getCommunityHomePath() {
    String path = getHomePath();
    if (Files.isDirectory(Paths.get(path, "community/.idea"))) {
      return path + "/community";
    }
    if (Files.isDirectory(Paths.get(path, "ultimate/community/.idea"))) {
      return path + "/ultimate/community";
    }
    return path;
  }

  public static @Nullable String getJarPathForClass(@NotNull Class<?> aClass) {
    String resourceRoot = getResourceRoot(aClass, '/' + aClass.getName().replace('.', '/') + ".class");
    return resourceRoot != null ? Paths.get(resourceRoot).toAbsolutePath().toString() : null;
  }

  public static @NotNull Collection<String> getUtilClassPath() {
    Set<String> classPath = new HashSet<>();

    @SuppressWarnings("UnnecessaryFullyQualifiedName") Class<?>[] classes = {
      PathManager.class,                               // module 'intellij.platform.util'
      Strings.class,                                   // module 'intellij.platform.util.strings'
      FList.class,                                     // module 'intellij.platform.util.collections'
      MinusculeMatcher.class,                          // module 'intellij.platform.util.text.matching'
      StartUpMeasurer.class,                           // module 'intellij.platform.util.diagnostic'
      com.intellij.openapi.util.SystemInfoRt.class,    // module 'intellij.platform.util.rt'
      com.intellij.util.lang.UrlClassLoader.class,     // module 'intellij.platform.util.classLoader'
      org.intellij.lang.annotations.Flow.class,        // jetbrains-annotations-java5
      org.jdom.Document.class,                         // jDOM
      org.apache.log4j.Appender.class,                 // Log4J
      gnu.trove.THashSet.class,                        // Trove
      Object2IntMap.class,                             // fastutil
      com.sun.jna.TypeMapper.class,                    // JNA
      com.sun.jna.platform.FileUtils.class,            // JNA (jna-platform)
      org.apache.oro.text.regex.PatternMatcher.class,  // OROMatcher
      net.jpountz.lz4.LZ4Factory.class,                // LZ4-Java
    };
    for (Class<?> aClass : classes) {
      String path = getJarPathForClass(aClass);
      if (path != null) {
        classPath.add(path);
      }
    }

    String resourceRoot = getResourceRoot(PathManager.class, "/messages/CommonBundle.properties");  // intellij.platform.resources.en
    if (resourceRoot != null) {
      classPath.add(Paths.get(resourceRoot).toAbsolutePath().toString());
    }

    return classPath;
  }

  // helpers

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void log(String x) {
    System.err.println(x);
  }

  public static @NotNull String getAbsolutePath(@NotNull String path) {
    if (path.startsWith("~/") || path.startsWith("~\\")) {
      path = SystemProperties.getUserHome() + path.substring(1);
    }
    return Paths.get(path).toAbsolutePath().normalize().toString();
  }

  private static @Nullable String getExplicitPath(String property) {
    String path = System.getProperty(property);
    return path != null ? getAbsolutePath(StringUtilRt.unquoteString(path, '"')) : null;
  }

  private static String platformPath(String selector,
                                     String macDir, String macSub,
                                     String winVar, String winSub,
                                     String xdgVar, String xdgDfl, String xdgSub) {
    String userHome = SystemProperties.getUserHome(), vendorName = vendorName();

    if (SystemInfoRt.isMac) {
      String dir = userHome + "/Library/" + macDir + '/' + vendorName;
      if (!selector.isEmpty()) dir = dir + '/' + selector;
      if (!macSub.isEmpty()) dir = dir + '/' + macSub;
      return dir;
    }

    if (SystemInfoRt.isWindows) {
      String dir = System.getenv(winVar);
      if (dir == null || dir.isEmpty()) dir = userHome + "\\AppData\\" + (winVar.startsWith("LOCAL") ? "Local" : "Roaming");
      dir = dir + '\\' + vendorName;
      if (!selector.isEmpty()) dir = dir + '\\' + selector;
      if (!winSub.isEmpty()) dir = dir + '\\' + winSub;
      return dir;
    }

    if (SystemInfoRt.isUnix) {
      String dir = System.getenv(xdgVar);
      if (dir == null || dir.isEmpty()) dir = userHome + '/' + xdgDfl;
      dir = dir + '/' + vendorName;
      if (!selector.isEmpty()) dir = dir + '/' + selector;
      if (!xdgSub.isEmpty()) dir = dir + '/' + xdgSub;
      return dir;
    }

    throw new UnsupportedOperationException("Unsupported OS: " + SystemInfoRt.OS_NAME);
  }

  private static String vendorName() {
    String property = System.getProperty(PROPERTY_VENDOR_NAME);
    if (property == null) {
      try {
        Class<?> ex = Class.forName("com.intellij.openapi.application.ex.ApplicationInfoEx");
        Class<?> impl = Class.forName("com.intellij.openapi.application.impl.ApplicationInfoImpl");
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Object instance = lookup.findStatic(impl, "getShadowInstance", MethodType.methodType(ex)).invoke();
        property = (String)lookup.findVirtual(impl, "getShortCompanyName", MethodType.methodType(String.class)).invoke(instance);
      }
      catch (Throwable ignored) { }
      System.setProperty(PROPERTY_VENDOR_NAME, property != null ? property : "JetBrains");
    }
    return property;
  }
}