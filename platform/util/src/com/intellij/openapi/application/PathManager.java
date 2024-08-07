// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.io.URLUtil;
import com.intellij.util.system.CpuArch;
import org.jetbrains.annotations.*;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
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
  public static final String PROPERTY_LOG_CONFIG_FILE = "idea.log.config.properties.file";
  public static final String PROPERTY_PATHS_SELECTOR = "idea.paths.selector";
  public static final String SYSTEM_PATHS_CUSTOMIZER = "idea.paths.customizer";

  public static final String OPTIONS_DIRECTORY = "options";
  public static final String DEFAULT_EXT = ".xml";

  private static final String PROPERTY_HOME = "idea.home";  // a reduced variant of PROPERTY_HOME_PATH, now deprecated
  private static final String PROPERTY_VENDOR_NAME = "idea.vendor.name";

  private static final String JRE_DIRECTORY = "jbr";
  private static final String LIB_DIRECTORY = "lib";
  private static final String PLUGINS_DIRECTORY = "plugins";
  private static final String BIN_DIRECTORY = "bin";
  private static final String LOG_DIRECTORY = "log";
  private static final String CONFIG_DIRECTORY = "config";
  private static final String SYSTEM_DIRECTORY = "system";
  private static final String COMMUNITY_MARKER = "intellij.idea.community.main.iml";
  private static final String ULTIMATE_MARKER = ".ultimate.root.marker";
  private static final String PRODUCT_INFO_JSON = "product-info.json";

  private static final class Lazy {
    private static final Pattern PROPERTY_REF = Pattern.compile("\\$\\{(.+?)}");
  }

  private static volatile String ourHomePath;
  private static volatile List<Path> ourBinDirectories;
  private static Path ourCommonDataPath;
  private static String ourPathSelector = System.getProperty(PROPERTY_PATHS_SELECTOR);
  private static String ourConfigPath;
  private static String ourSystemPath;
  private static String ourScratchPath;
  private static String ourPluginPath;
  private static String ourLogPath;
  private static Path ourStartupScriptDir;
  private static Path ourOriginalConfigDir;
  private static Path ourOriginalSystemDir;
  private static Path ourOriginalLogDir;
  private static Map<String, String> ourArchivedCompiledClassesMapping;

  /**
   * Returns paths to the directory where the IDE is installed, i.e., the directory containing 'lib', 'plugins' and other subdirectories.
   * On macOS, it's {@code <product>.app/Contents} directory.
   * <br>
   * If the IDE is started from source code rather than installation, the method returns paths to the Git repository root.
   * <br>
   * The method is supposed to be called from the main IDE process. For other processes started from the IDE process (e.g., build process)
   * use {@link #getHomePath(boolean)} with {@code false} argument.
   */
  public static @NotNull String getHomePath() {
    return getHomePath(true);
  }

  /**
   * A variant of {@link #getHomePath()} which also works inside additional processes started from the main IDE process.
   * @param insideIde {@code true} if the calling code works inside IDE; {@code false} otherwise (e.g., in a build process or a script)
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
          String advice = SystemInfoRt.isMac ? "reinstall the software." : "make sure bin/idea.properties is present in the installation directory.";
          throw new RuntimeException("Could not find installation home path. Please " + advice);
        }
      }

      if (result != null && SystemInfoRt.isWindows) {
        try {
          result = Paths.get(result).toRealPath(LinkOption.NOFOLLOW_LINKS).toString();
        }
        catch (IOException ignored) { }
      }

      // set before ourHomePath because getBinDirectories() rely on the fact that if `getHomePath(true)`
      // returns something, then `ourBinDirectories` is already computed
      if (result == null) {
        ourBinDirectories = Collections.emptyList();
      }
      else {
        Path root = Paths.get(result);
        if (Boolean.getBoolean("idea.use.dev.build.server")) {
          while (root.getParent() != null) {
            if (Files.exists(root.resolve(ULTIMATE_MARKER)) || Files.exists(root.resolve(COMMUNITY_MARKER))) {
              break;
            }
            root = root.getParent();
          }
        }
        ourBinDirectories = getBinDirectories(root);
      }

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
    try {
      return isUnderHomeDirectory(Paths.get(path));
    }
    catch (InvalidPathException e) {
      return false;
    }
  }

  public static boolean isUnderHomeDirectory(@NotNull Path target) {
    Path home = Paths.get(getHomePath());
    try {
      home = home.toRealPath();
      target = target.toRealPath();
    }
    catch (IOException ignored) { }
    return target.startsWith(home);
  }

  /**
   * Returns the path to the git repository of 'intellij' project when running tests from sources.
   * Consider using {@link #getHomeDirFor(Class)} instead.
   */
  @ApiStatus.Internal
  @TestOnly
  public static @Nullable String getHomePathFor(@NotNull Class<?> aClass) {
    Path result = getHomeDirFor(aClass);
    return result == null ? null : result.toString();
  }

  /**
   * Returns the path to the git repository of 'intellij' project when running tests from sources.
   * In production code, use {@link #getHomePath()} instead.
   */
  @ApiStatus.Internal
  @TestOnly
  public static @Nullable Path getHomeDirFor(@NotNull Class<?> aClass) {
    Path result = null;
    String rootPath = getResourceRoot(aClass, '/' + aClass.getName().replace('.', '/') + ".class");
    if (rootPath != null) {
      String relevantJarsRoot = getArchivedCompliedClassesLocation();
      if (relevantJarsRoot != null && rootPath.startsWith(relevantJarsRoot)) {
        String home = System.getProperty(PROPERTY_HOME_PATH);
        if (home != null) {
          Path path = Paths.get(home).toAbsolutePath();
          if (isIdeaHome(path)) {
            return path;
          }
        }
      }
      Path root = Paths.get(rootPath).toAbsolutePath();
      do root = root.getParent();
      while (root != null && !isIdeaHome(root));
      result = root;
    }
    return result;
  }

  private static boolean isIdeaHome(Path root) {
    return Files.isRegularFile(root.resolve(PRODUCT_INFO_JSON)) ||
           Files.isRegularFile(root.resolve("Resources").resolve(PRODUCT_INFO_JSON)) ||
           Files.isRegularFile(root.resolve(COMMUNITY_MARKER)) ||
           Files.isRegularFile(root.resolve(ULTIMATE_MARKER));
  }

  private static List<Path> getBinDirectories(Path root) {
    List<Path> binDirs = new ArrayList<>();

    Path[] candidates = {root.resolve(BIN_DIRECTORY), Paths.get(getCommunityHomePath(root.toString()), "bin")};
    String osSuffix = SystemInfoRt.isWindows ? "win" : SystemInfoRt.isMac ? "mac" : "linux";

    for (Path dir : candidates) {
      if (binDirs.contains(dir) || !Files.isDirectory(dir)) {
        continue;
      }

      binDirs.add(dir);
      dir = dir.resolve(osSuffix);
      if (Files.isDirectory(dir)) {
        binDirs.add(dir);
        if (SystemInfoRt.isWindows || SystemInfoRt.isLinux) {
          String arch = CpuArch.isIntel64() ? "amd64" : CpuArch.isArm64() ? "aarch64" : null;
          if (arch != null) {
            dir = dir.resolve(arch);
            if (Files.isDirectory(dir)) {
              binDirs.add(dir);
            }
          }
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
   * @return the first file that exists.
   * @throws RuntimeException if nothing found.
   * @see #findBinFile(String)
   */
  public static @NotNull Path findBinFileWithException(@NotNull String fileName) {
    Path file = findBinFile(fileName);
    if (file != null) {
      return file;
    }

    StringBuilder message = new StringBuilder();
    message.append('\'').append(fileName).append("' not found in directories:");
    for (Path directory : getBinDirectories()) {
      message.append('\n').append(directory);
    }
    throw new RuntimeException(message.toString());
  }

  /**
   * Returns the path to the directory where IDE's JAR files are stored.
   */
  public static @NotNull String getLibPath() {
    return getHomePath() + '/' + LIB_DIRECTORY;
  }

  /**
   * Returns the path to the directory where bundled plugins are located.
   */
  public static @NotNull String getPreInstalledPluginsPath() {
    return getHomePath() + '/' + PLUGINS_DIRECTORY;
  }

  /** <b>Note</b>: on macOS, the method returns a "functional" home, pointing to a JRE subdirectory inside a bundle. */
  public static @NotNull String getBundledRuntimePath() {
    return getHomePath() + '/' + JRE_DIRECTORY + (SystemInfoRt.isMac ? "/Contents/Home" : "");
  }

  /**
   * Returns the path to the directory where data common for all IntelliJ-based IDEs is stored. 
   */
  public static synchronized @NotNull Path getCommonDataPath() {
    Path path = ourCommonDataPath;
    if (path == null) {
      path = Paths.get(platformPath("", "Application Support", "", "APPDATA", "", "XDG_DATA_HOME", ".local/share", ""));
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
    return path;
  }

  /**
   * Returns a base name used to compose default locations of directories used by the IDE.
   */
  @ApiStatus.Internal
  @SuppressWarnings("IdentifierGrammar")
  public static @Nullable String getPathsSelector() {
    return ourPathSelector;
  }

  /**
   * Provides a way to update the path selected. This is a temporary solution, it'll be removed when RDCT-1474 is fixed.
   */
  @ApiStatus.Experimental
  @ApiStatus.Internal
  public static void setPathSelector(@NotNull String newValue) {
    ourPathSelector = newValue;
    System.setProperty(PROPERTY_PATHS_SELECTOR, newValue);
  }
  
  /**
   * Returns the path to the directory where settings are stored.
   * Usually, you don't need to access this directory directly, use {@link com.intellij.openapi.components.PersistentStateComponent} instead.
   */
  public static @NotNull Path getConfigDir() {
    return Paths.get(getConfigPath());
  }

  /**
   * Returns the path to the directory where settings are stored. Consider using {@link #getConfigDir()} instead.
   */
  public static @NotNull String getConfigPath() {
    String path = ourConfigPath;
    if (path == null) {
      String explicit = getExplicitPath(PROPERTY_CONFIG_PATH);
      ourConfigPath = path = explicit != null ? explicit :
                             ourPathSelector != null ? getDefaultConfigPathFor(ourPathSelector) :
                  getHomePath() + '/' + CONFIG_DIRECTORY;
    }
    return path;
  }

  @TestOnly
  public static void setExplicitConfigPath(@Nullable String path) {
    ourConfigPath = path;
  }

  /**
   * Returns the path to the directory where scratch files are stored.
   */
  public static @NotNull String getScratchPath() {
    String path = ourScratchPath;
    if (path == null) {
      String explicit = getExplicitPath(PROPERTY_SCRATCH_PATH);
      ourScratchPath = path = explicit == null ? getConfigPath() : explicit;
    }
    return path;
  }

  /**
   * Returns the path to the directory where settings are stored by default for IDE designated by {@code selector}.
   */
  public static @NotNull String getDefaultConfigPathFor(@NotNull String selector) {
    return platformPath(selector, "Application Support", "", "APPDATA", "", "XDG_CONFIG_HOME", ".config", "");
  }

  /**
   * Returns the path to the directory where regular settings are stored.
   * Usually, you don't need to access this directory directly, use {@link com.intellij.openapi.components.PersistentStateComponent} instead.
   */
  public static @NotNull String getOptionsPath() {
    return getConfigPath() + '/' + OPTIONS_DIRECTORY;
  }

  /**
   * Returns the path to a file with name {@code fileName} where regular settings are stored.
   * Usually, you don't need to access this directory directly, use {@link com.intellij.openapi.components.PersistentStateComponent} instead.
   */
  public static @NotNull File getOptionsFile(@NotNull String fileName) {
    return Paths.get(getOptionsPath(), fileName + DEFAULT_EXT).toFile();
  }

  /**
   * Returns the path to the directory where custom plugins are stored.
   */
  public static @NotNull Path getPluginsDir() {
    return Paths.get(getPluginsPath());
  }

  /**
   * Returns the path to the directory where custom plugins are stored. Consider using {@link #getPluginsDir()} instead.
   */
  public static @NotNull String getPluginsPath() {
    String path = ourPluginPath;
    if (path == null) {
      String explicit = getExplicitPath(PROPERTY_PLUGINS_PATH);
      ourPluginPath = path = explicit != null ? explicit :
                             ourPathSelector != null && System.getProperty(PROPERTY_CONFIG_PATH) == null ? getDefaultPluginPathFor(
                               ourPathSelector) :
        getConfigPath() + '/' + PLUGINS_DIRECTORY;
    }
    return path;
  }

  /**
   * Return the path to the directory where custom plugins are stored by default for IDE with the given path selector.
   */
  @ApiStatus.Internal
  public static @NotNull String getDefaultPluginPathFor(@NotNull String selector) {
    return platformPath(selector, "Application Support", PLUGINS_DIRECTORY, "APPDATA", PLUGINS_DIRECTORY, "XDG_DATA_HOME", ".local/share", "");
  }

  /**
   * Return the path to the directory where custom idea.properties and *.vmoptions files are stored.
   */
  public static @Nullable String getCustomOptionsDirectory() {
    // do not use getConfigPath() here - as it may be not yet defined
    return ourPathSelector != null ? getDefaultConfigPathFor(ourPathSelector) : null;
  }

  /**
   * Returns the path to the directory where caches are stored. 
   * To store plugin-related caches, always use a subdirectory named after the plugin.
   * To store caches related to a particular project, use 
   * {@link com.intellij.openapi.project.ProjectUtil#getProjectDataPath} instead.
   */
  public static @NotNull Path getSystemDir() {
    return Paths.get(getSystemPath());
  }

  /**
   * Returns the path to the directory where caches are stored.
   */
  public static @NotNull String getSystemPath() {
    String path = ourSystemPath;
    if (path == null) {
      String explicit = getExplicitPath(PROPERTY_SYSTEM_PATH);
      ourSystemPath = path = explicit != null ? explicit :
                             ourPathSelector != null ? getDefaultSystemPathFor(ourPathSelector) :
        getHomePath() + '/' + SYSTEM_DIRECTORY;
    }
    return path;
  }

  /**
   * Returns the path to the directory where caches are stored by default for IDE with the given path selector.
   */
  public static @NotNull String getDefaultSystemPathFor(@NotNull String selector) {
    return platformPath(selector, "Caches", "", "LOCALAPPDATA", "", "XDG_CACHE_HOME", ".cache", "");
  }

  @ApiStatus.Internal
  public static @NotNull String getDefaultUnixSystemPath(@NotNull String userHome, @NotNull String selector) {
    return getUnixPlatformPath(userHome, selector, null, ".cache", "");
  }

  /**
   * Returns the path to the directory to store temporary files.
   */
  public static @NotNull String getTempPath() {
    return getSystemPath() + "/tmp";
  }

  /**
   * Returns the path to the directory where indices are stored.
   */
  @ApiStatus.Internal
  public static @NotNull Path getIndexRoot() {
    String indexRootPath = getExplicitPath("index_root_path");
    if (indexRootPath == null) {
      indexRootPath = getSystemPath() + "/index";
    }
    return Paths.get(indexRootPath);
  }

  /**
   * Returns the path to the directory where log files are stored.
   * Usually you don't need to access it directly, use {@link com.intellij.openapi.diagnostic.Logger} instead.
   */
  public static @NotNull Path getLogDir() {
    return Paths.get(getLogPath());
  }

  /**
   * Returns the path to the directory where log files are stored. Consider using {@link #getLogDir()} instead.  
   */
  public static @NotNull String getLogPath() {
    String path = ourLogPath;
    if (path == null) {
      String explicit = getExplicitPath(PROPERTY_LOG_PATH);
      ourLogPath = path = explicit != null ? explicit :
                          ourPathSelector != null && System.getProperty(PROPERTY_SYSTEM_PATH) == null ? getDefaultLogPathFor(
                            ourPathSelector) :
        getSystemPath() + '/' + LOG_DIRECTORY;
    }
    return path;
  }

  /**
   * Returns the path to the directory where log files are stored by default for IDE with the given path selector.
   */
  @ApiStatus.Internal
  public static @NotNull String getDefaultLogPathFor(@NotNull String selector) {
    return platformPath(selector, "Logs", "", "LOCALAPPDATA", LOG_DIRECTORY, "XDG_CACHE_HOME", ".cache", LOG_DIRECTORY);
  }

  /**
   * Returns the path to the directory where the script which is executed at startup and files used by it are located.
   * @see com.intellij.ide.startup.StartupActionScriptManager
   */
  @ApiStatus.Internal
  public static @NotNull Path getStartupScriptDir() {
    if (ourStartupScriptDir != null) return ourStartupScriptDir;
    return getSystemDir().resolve(PLUGINS_DIRECTORY);
  }

  /**
   * This method isn't supposed to be used in new code. If you need to locate a directory where the startup script and related files are
   * located, use {@link #getStartupScriptDir()} instead. If you need to save some custom caches related to plugins, create your own
   * directory under {@link #getSystemDir()}.
   */
  @ApiStatus.Obsolete
  public static @NotNull String getPluginTempPath() {
    return getSystemPath() + '/' + PLUGINS_DIRECTORY;
  }

  // misc stuff

  /**
   * Attempts to detect classpath entry containing the resource.
   */
  public static @Nullable String getResourceRoot(@NotNull Class<?> context, @NotNull String path) {
    URL url = context.getResource(path);
    if (url == null) {
      url = ClassLoader.getSystemResource(path.substring(1));
    }
    return url != null ? extractRoot(url, path) : null;
  }

  /**
   * Attempts to detect classpath entry containing the resource.
   */
  public static @Nullable String getResourceRoot(@NotNull ClassLoader classLoader, @NotNull String resourcePath) {
    URL url = classLoader.getResource(resourcePath);
    return url == null ? null : extractRoot(url, "/" + resourcePath);
  }

  /**
   * Attempts to extract classpath entry part from passed URL.
   */
  private static @Nullable String extractRoot(URL resourceURL, String resourcePath) {
    if (resourcePath.isEmpty() || resourcePath.charAt(0) != '/' && resourcePath.charAt(0) != '\\') {
      log("precondition failed: " + resourcePath);
      return null;
    }

    String resultPath = null;
    String protocol = resourceURL.getProtocol();
    if (URLUtil.FILE_PROTOCOL.equals(protocol)) {
      File result;
      try {
        result = new File(resourceURL.toURI().getSchemeSpecificPart());
      }
      catch (URISyntaxException e) {
        throw new IllegalArgumentException("URL='" + resourceURL + "'", e);
      }
      String path = result.getPath();
      String testPath = path.replace('\\', '/');
      String testResourcePath = resourcePath.replace('\\', '/');
      if (StringUtilRt.endsWithIgnoreCase(testPath, testResourcePath)) {
        resultPath = path.substring(0, path.length() - resourcePath.length());
      }
    }
    else if (URLUtil.JAR_PROTOCOL.equals(protocol)) {
      // do not use URLUtil.splitJarUrl here - used in bootstrap
      String jarPath = splitJarUrl(resourceURL.getFile());
      if (jarPath != null) {
        resultPath = jarPath;
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

  // do not use URLUtil.splitJarUrl here - used in bootstrap
  private static @Nullable String splitJarUrl(@NotNull String url) {
    int pivot = url.indexOf(URLUtil.JAR_SEPARATOR);
    if (pivot < 0) {
      return null;
    }

    String jarPath = url.substring(0, pivot);

    boolean startsWithConcatenation = true;
    int offset = 0;
    for (String prefix : new String[]{URLUtil.JAR_PROTOCOL, ":"}) {
      int prefixLen = prefix.length();
      if (!jarPath.regionMatches(offset, prefix, 0, prefixLen)) {
        startsWithConcatenation = false;
        break;
      }
      offset += prefixLen;
    }
    if (startsWithConcatenation) {
      jarPath = jarPath.substring(URLUtil.JAR_PROTOCOL.length() + 1);
    }

    if (!jarPath.startsWith(URLUtil.FILE_PROTOCOL)) {
      return jarPath;
    }

    try {
      File result;
      URL parsedUrl = new URL(jarPath);
      try {
        result = new File(parsedUrl.toURI().getSchemeSpecificPart());
      }
      catch (URISyntaxException e) {
        throw new IllegalArgumentException("URL='" + parsedUrl + "'", e);
      }
      return result.getPath().replace('\\', '/');
    }
    catch (Exception e) {
      jarPath = jarPath.substring(URLUtil.FILE_PROTOCOL.length());
      if (jarPath.startsWith(URLUtil.SCHEME_SEPARATOR)) {
        return jarPath.substring(URLUtil.SCHEME_SEPARATOR.length());
      }
      else if (!jarPath.isEmpty() && jarPath.charAt(0) == ':') {
        return jarPath.substring(1);
      }
      else {
        return jarPath;
      }
    }
  }

  @ApiStatus.Internal
  public static void loadProperties() {
    List<Path> files = new ArrayList<>();
    String customFile = System.getProperty(PROPERTIES_FILE);
    if (customFile != null) {
      files.add(Paths.get(customFile));
    }
    String optionsDir = getCustomOptionsDirectory();
    if (optionsDir != null) {
      files.add(Paths.get(optionsDir, PROPERTIES_FILE_NAME));
    }
    files.add(Paths.get(System.getProperty("user.home"), PROPERTIES_FILE_NAME));
    for (Path binDir : getBinDirectories()) {
      files.add(binDir.resolve(PROPERTIES_FILE_NAME));
    }

    Properties sysProperties = System.getProperties();
    String homePath = getHomePath(true);
    for (Path file : files) {
      try (Reader reader = Files.newBufferedReader(file)) {
        //noinspection NonSynchronizedMethodOverridesSynchronizedMethod
        new Properties() {
          @Override
          public Object put(Object key, Object value) {
            if (PROPERTY_HOME_PATH.equals(key) || PROPERTY_HOME.equals(key)) {
              log(file + ": '" + key + "' cannot be redefined");
            }
            else if (!sysProperties.containsKey(key)) {
              sysProperties.setProperty((String)key, substituteVars((String)value, homePath));
            }
            return null;
          }
        }.load(reader);
      }
      catch (NoSuchFileException | AccessDeniedException ignore) { }
      catch (IOException e) {
        log("Can't read property file '" + file + "': " + e.getMessage());
      }
    }

    // check and fix conflicting properties
    if (SystemInfoRt.isJBSystemMenu) {
      sysProperties.setProperty("apple.laf.useScreenMenuBar", "false");
    }
  }

  @ApiStatus.Internal
  public static void customizePaths(List<String> args) {
    String property = System.getProperty(SYSTEM_PATHS_CUSTOMIZER);
    if (property == null) return;

    try {
      Class<?> aClass = PathManager.class.getClassLoader().loadClass(property);
      Object customizer = aClass.getConstructor().newInstance();
      if (customizer instanceof PathCustomizer) {
        PathCustomizer.CustomPaths paths = ((PathCustomizer)customizer).customizePaths(args);
        if (paths != null) {
          ourOriginalConfigDir = getConfigDir();
          ourOriginalSystemDir = getSystemDir();
          ourOriginalLogDir = getLogDir();
          if (paths.configPath != null) System.setProperty(PROPERTY_CONFIG_PATH, paths.configPath);
          if (paths.systemPath != null) System.setProperty(PROPERTY_SYSTEM_PATH, paths.systemPath);
          if (paths.pluginsPath != null) System.setProperty(PROPERTY_PLUGINS_PATH, paths.pluginsPath);
          if (paths.logDirPath != null) System.setProperty(PROPERTY_LOG_PATH, paths.logDirPath);

          if (paths.startupScriptDir != null) ourStartupScriptDir = paths.startupScriptDir;
          // NB: IDE might use an instance from a different classloader
          ourConfigPath = null;
          ourSystemPath = null;
          ourPluginPath = null;
          ourScratchPath = null;
          ourLogPath = null;
        }
      }
    }
    catch (Throwable e) {
      log("Failed to set up '" + property + "' as PathCustomizer: " + e);
    }
  }

  /**
   * Return original value of the config path ignoring possible customizations made by {@link PathCustomizer}. 
   */
  @ApiStatus.Internal
  public static @NotNull Path getOriginalConfigDir() {
    return ourOriginalConfigDir != null ? ourOriginalConfigDir : getConfigDir();
  }

  /**
   * Return original value of the system path ignoring possible customizations made by {@link PathCustomizer}.
   */
  @ApiStatus.Internal
  public static @NotNull Path getOriginalSystemDir() {
    return ourOriginalSystemDir != null ? ourOriginalSystemDir : getSystemDir();
  }

  /**
   * Return original value of the log path ignoring possible customizations made by {@link PathCustomizer}.
   */
  @ApiStatus.Internal
  public static @NotNull Path getOriginalLogDir() {
    return ourOriginalLogDir != null ? ourOriginalLogDir : getLogDir();
  }

  @ApiStatus.Internal
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

  @ApiStatus.Internal
  public static @NotNull File findFileInLibDirectory(@NotNull String relativePath) {
    Path file = Paths.get(getLibPath(), relativePath);
    if (!Files.exists(file)) file = Paths.get(getHomePath(), "community/lib/" + relativePath);
    return file.toFile();
  }

  /**
   * @return path to 'community' project home irrespective of the current project
   */
  public static @NotNull String getCommunityHomePath() {
    return getCommunityHomePath(getHomePath());
  }

  private static boolean isDevServer() {
    return Boolean.getBoolean("idea.use.dev.build.server");
  }

  private static @NotNull String getCommunityHomePath(@NotNull String homePath) {
    boolean isRunningFromSources = Files.isDirectory(Paths.get(homePath, ".idea"));
    if (!isRunningFromSources && !isDevServer()) return homePath;
    ArrayList<Path> possibleCommunityPathList = new ArrayList<>();
    possibleCommunityPathList.add(Paths.get(homePath, "community"));
    possibleCommunityPathList.add(Paths.get(homePath, "..", "..", "..", "community"));
    possibleCommunityPathList.add(Paths.get(homePath, "..", "..", "..", "..", "community"));
    for (Path possibleCommunityPath : possibleCommunityPathList) {
      if (Files.isRegularFile(possibleCommunityPath.resolve(COMMUNITY_MARKER))) {
        return possibleCommunityPath.normalize().toString();
      }
    }
    return homePath;
  }

  /**
   * Returns the path to the JAR file or root directory from where class-file for {@code aClass} is located.
   * Consider using {@link #getJarForClass(Class)} instead.
   */
  public static @Nullable String getJarPathForClass(@NotNull Class<?> aClass) {
    Path resourceRoot = getJarForClass(aClass);
    return resourceRoot == null ? null : resourceRoot.toString();
  }

  /**
   * Returns the path to the JAR file or root directory from where class-file for {@code aClass} is located.
   */
  public static @Nullable Path getJarForClass(@NotNull Class<?> aClass) {
    String resourceRoot = getResourceRoot(aClass, '/' + aClass.getName().replace('.', '/') + ".class");
    return resourceRoot == null ? null : Paths.get(resourceRoot).toAbsolutePath();
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void log(String x) {
    System.err.println(x);
  }

  /**
   * Convert the given path to an absolute path substituting '~' symbol with the user home path.
   */
  public static @NotNull String getAbsolutePath(@NotNull String path) {
    if (path.startsWith("~/") || path.startsWith("~\\")) {
      path = System.getProperty("user.home") + path.substring(1);
    }
    return Paths.get(path).toAbsolutePath().normalize().toString();
  }

  private static @Nullable String getExplicitPath(@NotNull String property) {
    String path = System.getProperty(property);
    if (path == null) {
      return null;
    }

    boolean quoted = path.length() > 1 && '"' == path.charAt(0) && '"' == path.charAt(path.length() - 1);
    return getAbsolutePath(quoted ? path.substring(1, path.length() - 1) : path);
  }

  private static String platformPath(String selector,
                                     String macDir, String macSub,
                                     String winVar, String winSub,
                                     String xdgVar, String xdgDfl, String xdgSub) {
    String userHome = System.getProperty("user.home");
    String vendorName = vendorName();

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
      return getUnixPlatformPath(userHome, selector, xdgVar, xdgDfl, xdgSub);
    }

    throw new UnsupportedOperationException("Unsupported OS: " + SystemInfoRt.OS_NAME);
  }

  private static String getUnixPlatformPath(String userHome, String selector, @Nullable String xdgVar, String xdgDfl, String xdgSub) {
    String dir = xdgVar != null ? System.getenv(xdgVar) : null;
    if (dir == null || dir.isEmpty()) dir = userHome + '/' + xdgDfl;
    dir = dir + '/' + vendorName();
    if (!selector.isEmpty()) dir = dir + '/' + selector;
    if (!xdgSub.isEmpty()) dir = dir + '/' + xdgSub;
    return dir;
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

  /**
   * NB: actual jars might be in subdirectories
   */
  @ApiStatus.Internal
  public static @Nullable String getArchivedCompliedClassesLocation() {
    return System.getProperty("intellij.test.jars.location");
  }

  /**
   * Returns map of IntelliJ modules to jar absolute paths, e.g.:
   * "production/intellij.platform.util" => ".../production/intellij.platform.util/$hash.jar"
   */
  @ApiStatus.Internal
  public static @Nullable Map<String, String> getArchivedCompiledClassesMapping() {
    if (ourArchivedCompiledClassesMapping == null) {
      ourArchivedCompiledClassesMapping = computeArchivedCompiledClassesMapping();
    }
    return ourArchivedCompiledClassesMapping;
  }

  private static @Nullable Map<String, String> computeArchivedCompiledClassesMapping() {
    final String filePath = System.getProperty("intellij.test.jars.mapping.file");
    if (StringUtilRt.isEmptyOrSpaces(filePath)) {
      return null;
    }
    final List<String> lines;
    try {
      lines = Files.readAllLines(Paths.get(filePath));
    }
    catch (Exception e) {
      log("Failed to load jars mappings from " + filePath);
      return null;
    }
    final Map<String, String> mapping = new HashMap<>(lines.size());
    for (String line : lines) {
      String[] split = line.split("=", 2);
      if (split.length < 2) {
        log("Ignored jars mapping line: " + line);
        continue;
      }
      mapping.put(split[0], split[1]);
    }
    return Collections.unmodifiableMap(mapping);
  }
}
