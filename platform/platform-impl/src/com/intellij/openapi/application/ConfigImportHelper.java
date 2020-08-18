// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.diagnostic.VMOptions;
import com.intellij.ide.actions.ImportSettingsFilenameFilter;
import com.intellij.ide.cloudConfig.CloudConfigProvider;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginDescriptorLoader;
import com.intellij.ide.plugins.PluginInstaller;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.idea.Main;
import com.intellij.idea.SplashManager;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.Restarter;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.Decompressor;
import com.intellij.util.text.VersionComparatorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import static com.intellij.ide.GeneralSettings.IDE_GENERAL_XML;
import static com.intellij.openapi.application.CustomConfigMigrationOption.readCustomConfigMigrationOptionAndRemoveMarkerFile;
import static com.intellij.openapi.application.PathManager.OPTIONS_DIRECTORY;
import static com.intellij.openapi.util.Pair.pair;

public final class ConfigImportHelper {
  private static final String FIRST_SESSION_KEY = "intellij.first.ide.session";
  private static final String CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY = "intellij.config.imported.in.current.session";

  private static final String CONFIG = "config";
  private static final String[] OPTIONS = {
    OPTIONS_DIRECTORY + '/' + StoragePathMacros.NON_ROAMABLE_FILE,
    OPTIONS_DIRECTORY + '/' + IDE_GENERAL_XML,
    OPTIONS_DIRECTORY + "/options.xml"};
  private static final String BIN = "bin";
  private static final String CONTENTS = "Contents";
  private static final String PLIST = "Info.plist";
  private static final String PLUGINS = "plugins";
  private static final String SYSTEM = "system";

  private static final Pattern SELECTOR_PATTERN = Pattern.compile("\\.?([^\\d]+)(\\d+(?:\\.\\d+)?)");
  private static final String SHOW_IMPORT_CONFIG_DIALOG_PROPERTY = "idea.initially.ask.config";

  // constant is used instead of util method to ensure that ConfigImportHelper class is not loaded by StartupUtil
  public static final String CUSTOM_MARKER_FILE_NAME = "migrate.config";

  private ConfigImportHelper() { }

  public static void importConfigsTo(boolean veryFirstStartOnThisComputer, @NotNull Path newConfigDir, @NotNull Logger log) {
    log.info("Importing configs to " + newConfigDir);
    System.setProperty(FIRST_SESSION_KEY, Boolean.TRUE.toString());

    CustomConfigMigrationOption customMigrationOption = readCustomConfigMigrationOptionAndRemoveMarkerFile(newConfigDir);

    if (customMigrationOption instanceof CustomConfigMigrationOption.SetProperties) {
      List<String> properties = ((CustomConfigMigrationOption.SetProperties)customMigrationOption).getProperties();
      log.info("Enabling system properties after restart: " + properties);
      for (String property : properties) {
        System.setProperty(property, Boolean.TRUE.toString());
      }
      return;
    }

    ConfigImportSettings settings = null;
    try {
      String customProviderName = "com.intellij.openapi.application." + PlatformUtils.getPlatformPrefix() + "ConfigImportSettings";
      @SuppressWarnings("unchecked") Class<ConfigImportSettings> customProviderClass = (Class<ConfigImportSettings>)Class.forName(customProviderName);
      if (ConfigImportSettings.class.isAssignableFrom(customProviderClass)) {
        settings = ReflectionUtil.newInstance(customProviderClass);
      }
    }
    catch (Exception ignored) { }

    @NotNull List<PathAndFileTime> guessedOldConfigDirs = findConfigDirectories(newConfigDir);
    File tempBackup = null;
    boolean vmOptionFileChanged = false;

    try {
      Pair<Path, Path> oldConfigDirAndOldIdePath = null;
      if (customMigrationOption != null) {
        log.info("Custom migration option: " + customMigrationOption);
        vmOptionFileChanged = doesVmOptionFileExist(newConfigDir);
        try {
          if (customMigrationOption instanceof CustomConfigMigrationOption.MigrateFromCustomPlace) {
            tempBackup = backupCurrentConfigToTempAndDelete(newConfigDir, log, false);
            Path location = ((CustomConfigMigrationOption.MigrateFromCustomPlace)customMigrationOption).getLocation();
            oldConfigDirAndOldIdePath = findConfigDirectoryByPath(location);
          }
          else {
            tempBackup = backupCurrentConfigToTempAndDelete(newConfigDir, log, true);
          }
        }
        catch (IOException e) {
          log.error("Couldn't backup current config or delete current config directory", e);
        }
      }
      else if (shouldAskForConfig()) {
        oldConfigDirAndOldIdePath = showDialogAndGetOldConfigPath(guessedOldConfigDirs);
      }
      else if (guessedOldConfigDirs.isEmpty()) {
        boolean importedFromCloud = false;
        CloudConfigProvider configProvider = CloudConfigProvider.getProvider();
        if (configProvider != null) {
          importedFromCloud = configProvider.importSettingsSilently(newConfigDir);
        }
        if (!importedFromCloud && !veryFirstStartOnThisComputer) {
          oldConfigDirAndOldIdePath = showDialogAndGetOldConfigPath(guessedOldConfigDirs);
        }
      }
      else {
        PathAndFileTime bestConfigGuess = guessedOldConfigDirs.get(0);
        if (isConfigOld(bestConfigGuess.fileTime)) {
          oldConfigDirAndOldIdePath = showDialogAndGetOldConfigPath(guessedOldConfigDirs);
        }
        else {
          oldConfigDirAndOldIdePath = findConfigDirectoryByPath(bestConfigGuess.path);
        }
      }

      if (oldConfigDirAndOldIdePath != null) {
        doImport(oldConfigDirAndOldIdePath.first, newConfigDir, oldConfigDirAndOldIdePath.second, log);

        if (settings != null) {
          settings.importFinished(newConfigDir);
        }

        System.setProperty(CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY, Boolean.TRUE.toString());
      }
      else {
        log.info("No configs imported, starting with clean configs at " + newConfigDir);
      }

      vmOptionFileChanged |= doesVmOptionFileExist(newConfigDir);
    }
    finally {
      if (tempBackup != null) {
        try {
          moveTempBackupToStandardBackup(tempBackup, log);
        }
        catch (IOException e) {
          log.warn(String.format("Couldn't move the backup of current config from temp dir [%s] to backup dir [%s]",
                                 tempBackup, getBackupPath()), e);
        }
      }
    }

    if (vmOptionFileChanged) {
      log.info("The vmoptions file has changed, restarting...");

      List<String> properties = new ArrayList<>();
      properties.add(FIRST_SESSION_KEY);
      if (isConfigImported()) {
        properties.add(CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY);
      }
      new CustomConfigMigrationOption.SetProperties(properties).writeConfigMarkerFile();

      restart();
    }
  }

  private static boolean isConfigOld(@NotNull FileTime time) {
    Instant deadline = Instant.now().minus(6 * 30, ChronoUnit.DAYS);
    return time.toInstant().compareTo(deadline) < 0;
  }

  private static boolean doesVmOptionFileExist(@NotNull Path configDir) {
    return Files.isRegularFile(configDir.resolve(VMOptions.getCustomVMOptionsFileName()));
  }

  private static void restart() {
    if (Restarter.isSupported()) {
      try {
        Restarter.scheduleRestart(false);
      }
      catch (IOException e) {
        Main.showMessage("Restart failed", e);
      }
      System.exit(0);
    }
    else {
      String title = ApplicationBundle.message("title.import.settings", ApplicationNamesInfo.getInstance().getFullProductName());
      String message = ApplicationBundle.message("restart.import.settings");
      String yes = ApplicationBundle.message("restart.import.now"), no = ApplicationBundle.message("restart.import.later");
      if (Messages.showYesNoDialog(message, title, yes, no, Messages.getQuestionIcon()) == Messages.YES) {
        System.exit(0);
      }
    }
  }

  @NotNull
  private static File backupCurrentConfigToTempAndDelete(@NotNull Path currentConfig, @NotNull Logger log, boolean smartDelete) throws IOException {
    File tempBackupDir = FileUtil.createTempDirectory(getConfigDirName(), "-backup");
    log.info("Backup config from " + currentConfig + " to " + tempBackupDir);
    FileUtil.copyDir(PathManager.getConfigDir().toFile(), tempBackupDir);

    deleteCurrentConfigDir(currentConfig, log, smartDelete);

    Path pluginsDir = currentConfig.getFileSystem().getPath(PathManager.getPluginsPath());
    if (Files.exists(pluginsDir) && !pluginsDir.startsWith(currentConfig)) {
      File pluginsBackup = new File(tempBackupDir, PLUGINS);
      log.info("Backup plugins dir separately from " + pluginsDir + " to " + pluginsBackup);
      if (pluginsBackup.mkdir()) {
        FileUtil.copyDir(new File(pluginsDir.toString()), pluginsBackup);
        FileUtil.delete(pluginsDir);
      }
      else {
        log.warn("Couldn't backup plugins directory to " + pluginsBackup);
      }
    }

    return tempBackupDir;
  }

  private static void deleteCurrentConfigDir(@NotNull Path currentConfig, @NotNull Logger log, boolean smartDelete) throws IOException {
    log.debug("Removing current config directory, smartDelete: " + smartDelete);
    if (!smartDelete) {
      FileUtil.delete(currentConfig);
      return;
    }

    boolean removedViaCustomizer = false;
    try {
      for (RestoreDefaultConfigCustomizer customizer : ServiceLoader.load(RestoreDefaultConfigCustomizer.class)) {
        log.debug("Found " + customizer);
        customizer.removeCurrentConfigDir(currentConfig);
        removedViaCustomizer = true;
        break;
      }
    }
    catch (Exception e) {
      log.warn("Couldn't remove current config dir using the customizer", e);
    }

    if (!removedViaCustomizer) {
      log.debug("RestoreDefaultConfigCustomizer not found, removing config directory manually...");
      FileUtil.delete(currentConfig);
    }
  }

  private static void moveTempBackupToStandardBackup(@NotNull File backupToMove,
                                                     @NotNull Logger log) throws IOException {
    Path backupPath = getBackupPath();
    log.info("Move backup from " + backupToMove + " to " + backupPath);
    FileUtil.delete(backupPath);
    FileUtil.copyDir(backupToMove, backupPath.toFile());
  }

  @NotNull
  public static Path getBackupPath() {
    Path configDir = PathManager.getConfigDir();
    return configDir.resolveSibling(getConfigDirName() + "-backup");
  }

  @NotNull
  private static String getConfigDirName() {
    return PathManager.getConfigDir().getFileName().toString();
  }

  private static boolean shouldAskForConfig() {
    String showImportDialog = System.getProperty(SHOW_IMPORT_CONFIG_DIALOG_PROPERTY);
    if ("force-not".equals(showImportDialog)) {
      return false;
    }
    return PluginManagerCore.isRunningFromSources() ||
           System.getProperty(PathManager.PROPERTY_CONFIG_PATH) != null ||
           "true".equals(showImportDialog);
  }

  @Nullable
  private static Pair<Path, Path> showDialogAndGetOldConfigPath(@NotNull List<PathAndFileTime> guessedOldConfigDirs) {
    ImportOldConfigsPanel dialog = new ImportOldConfigsPanel(ContainerUtil.map(guessedOldConfigDirs, it -> it.path),
                                                             ConfigImportHelper::findConfigDirectoryByPath);
    dialog.setModalityType(Dialog.ModalityType.TOOLKIT_MODAL);
    AppUIUtil.updateWindowIcon(dialog);

    Ref<Pair<Path, Path>> result = new Ref<>();
    SplashManager.executeWithHiddenSplash(dialog, () -> {
      dialog.setVisible(true);
      result.set(dialog.getSelectedFile());
      dialog.dispose();
    });
    return result.get();
  }

  /** Returns {@code true} when the IDE is launched for the first time (i.e. there was no config directory). */
  public static boolean isFirstSession() {
    return Boolean.getBoolean(FIRST_SESSION_KEY);
  }

  /** Simple check by file type, content is not checked. */
  public static boolean isSettingsFile(@NotNull VirtualFile file) {
    return FileTypeRegistry.getInstance().isFileOfType(file, ArchiveFileType.INSTANCE);
  }

  /** Returns {@code true} when the IDE is launched for the first time, and configs were imported from another installation. */
  public static boolean isConfigImported() {
    return Boolean.getBoolean(CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY);
  }

  static boolean isValidSettingsFile(@NotNull File file) {
    try (ZipFile zip = new ZipFile(file)) {
      return zip.getEntry(ImportSettingsFilenameFilter.SETTINGS_JAR_MARKER) != null;
    }
    catch (IOException ignored) {
      return false;
    }
  }

  public static boolean isConfigDirectory(@NotNull Path candidate) {
    return Arrays.stream(OPTIONS).anyMatch(name -> Files.exists(candidate.resolve(name)));
  }

  static class PathAndFileTime {
    final Path path;
    final FileTime fileTime;

    PathAndFileTime(@NotNull Path path, @NotNull FileTime fileTime) {
      this.path = path;
      this.fileTime = fileTime;
    }
  }

  static @NotNull List<PathAndFileTime> findConfigDirectories(@NotNull Path newConfigDir) {
    // looking for existing config directories ...
    Set<Path> homes = new HashSet<>();
    homes.add(newConfigDir.getParent());  // ... in the vicinity of the new config directory
    homes.add(newConfigDir.getFileSystem().getPath(PathManager.getDefaultConfigPathFor("X")).getParent());  // ... in the default location
    Path historic = newConfigDir.getFileSystem().getPath(defaultConfigPath("X2019.3"));
    homes.add(SystemInfo.isMac ? historic.getParent() : historic.getParent().getParent());  // ... in the historic location

    String prefix = getPrefixFromSelector(PathManager.getPathsSelector());
    if (prefix == null) prefix = getPrefixFromSelector(getNameWithVersion(newConfigDir));
    if (prefix == null) {
      String productName = ApplicationNamesInfo.getInstance().getFullProductName();
      if (productName != null) prefix = productName.replace(" ", "");
    }
    if (prefix == null) prefix = PlatformUtils.getPlatformPrefix();
    String dotPrefix = '.' + prefix;

    List<Path> candidates = new ArrayList<>();
    for (Path home : homes) {
      if (home == null || !Files.isDirectory(home)) continue;

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(home)) {
        for (Path path : stream) {
          if (!path.equals(newConfigDir)) {
            String name = path.getFileName().toString();
            if ((StringUtil.startsWithIgnoreCase(name, prefix) || StringUtil.startsWithIgnoreCase(name, dotPrefix)) && Files.isDirectory(path)) {
              candidates.add(path);
            }
          }
        }
      }
      catch (IOException ignore) { }
    }
    if (candidates.isEmpty()) {
      return Collections.emptyList();
    }

    List<PathAndFileTime> lastModified = new ArrayList<>();
    for (Path child : candidates) {
      Path candidate = child, config = child.resolve(CONFIG);
      if (Files.isDirectory(config)) candidate = config;

      FileTime max = null;
      for (String name : OPTIONS) {
        try {
          FileTime cur = Files.getLastModifiedTime(candidate.resolve(name));
          if (max == null || cur.compareTo(max) > 0) {
            max = cur;
          }
        }
        catch (IOException ignore) { }
      }

      lastModified.add(new PathAndFileTime(candidate, max != null ? max : FileTime.fromMillis(0)));
    }

    lastModified.sort((o1, o2) -> {
      int diff = o2.fileTime.compareTo(o1.fileTime);
      if (diff == 0) {
        diff = StringUtil.naturalCompare(o2.path.toString(), o1.path.toString());
      }
      return diff;
    });
    return lastModified;
  }

  private static String getNameWithVersion(Path configDir) {
    String name = configDir.getFileName().toString();
    if (CONFIG.equals(name)) name = StringUtil.trimStart(configDir.getParent().getFileName().toString(), ".");
    return name;
  }

  private static @Nullable String getPrefixFromSelector(@Nullable String nameWithSelector) {
    if (nameWithSelector != null) {
      Matcher m = SELECTOR_PATTERN.matcher(nameWithSelector);
      if (m.matches()) {
        return m.group(1);
      }
    }
    return null;
  }

  private static @Nullable Pair<Path, Path> findConfigDirectoryByPath(Path selectedDir) {
    // tries to map a user selection into a valid config directory
    // returns a pair of a config directory and an IDE home (when a user pointed to it; null otherwise)

    if (isConfigDirectory(selectedDir)) {
      return pair(selectedDir, null);
    }

    Path config = selectedDir.resolve(CONFIG);
    if (isConfigDirectory(config)) {
      return pair(config, null);
    }

    if (Files.isDirectory(selectedDir.resolve(SystemInfo.isMac ? CONTENTS : BIN))) {
      Path configDir = getSettingsPath(selectedDir, PathManager.PROPERTY_CONFIG_PATH, ConfigImportHelper::defaultConfigPath);
      if (configDir != null && isConfigDirectory(configDir)) {
        return pair(configDir, selectedDir);
      }
    }

    return null;
  }

  private static @Nullable Path getSettingsPath(Path ideHome, String propertyName, Function<String, String> pathBySelector) {
    List<Path> files = new ArrayList<>();
    if (SystemInfo.isMac) {
      files.add(ideHome.resolve(CONTENTS + '/' + BIN + '/' + PathManager.PROPERTIES_FILE_NAME));
      files.add(ideHome.resolve(CONTENTS + '/' + PLIST));
    }
    else {
      files.add(ideHome.resolve(BIN + '/' + PathManager.PROPERTIES_FILE_NAME));
      String scriptName = ApplicationNamesInfo.getInstance().getScriptName();
      files.add(ideHome.resolve(BIN + '/' + scriptName + ".bat"));
      files.add(ideHome.resolve(BIN + '/' + scriptName + ".sh"));
    }

    // explicitly specified directory
    for (Path file : files) {
      if (Files.isRegularFile(file)) {
        String candidatePath = PathManager.substituteVars(getPropertyFromFile(file, propertyName), ideHome.toString());
        if (candidatePath != null) {
          Path candidate = ideHome.getFileSystem().getPath(candidatePath);
          if (Files.isDirectory(candidate)) {
            return candidate.toAbsolutePath();
          }
        }
      }
    }

    // default directory
    for (Path file : files) {
      if (Files.isRegularFile(file)) {
        String selector = getPropertyFromFile(file, PathManager.PROPERTY_PATHS_SELECTOR);
        if (selector != null) {
          Path candidate = ideHome.getFileSystem().getPath(pathBySelector.apply(selector));
          if (Files.isDirectory(candidate)) {
            return candidate;
          }
        }
      }
    }

    return null;
  }

  private static @Nullable String getPropertyFromFile(Path file, String propertyName) {
    try {
      String fileContent = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);

      final String fileName = file.getFileName().toString();
      if (fileName.endsWith(".properties")) {
        PropertyResourceBundle bundle = new PropertyResourceBundle(new StringReader(fileContent));
        return bundle.containsKey(propertyName) ? bundle.getString(propertyName) : null;
      }

      if (fileName.endsWith(".plist")) {
        String propertyValue = findPListKey(propertyName, fileContent);
        if (!StringUtil.isEmpty(propertyValue)) {
          return propertyValue;
        }
      }

      String propertyValue = findProperty(propertyName, fileContent);
      if (!StringUtil.isEmpty(propertyValue)) {
        return propertyValue;
      }
    }
    catch (IOException ignored) { }

    return null;
  }

  private static @Nullable String findPListKey(String propertyName, String fileContent) {
    String key = "<key>" + propertyName + "</key>";
    int idx = fileContent.indexOf(key);
    if (idx > 0) {
      idx = fileContent.indexOf("<string>", idx + key.length());
      if (idx != -1) {
        idx += "<string>".length();
        return fixDirName(fileContent.substring(idx, fileContent.indexOf("</string>", idx)));
      }
    }

    return null;
  }

  private static @Nullable String findProperty(String propertyName, String fileContent) {
    String prefix = propertyName + "=";
    int idx = fileContent.indexOf(prefix);
    if (idx >= 0) {
      StringBuilder configDir = new StringBuilder();
      idx += prefix.length();
      if (fileContent.length() > idx) {
        boolean quoted = fileContent.charAt(idx) == '"';
        if (quoted) idx++;
        while (fileContent.length() > idx &&
               (quoted ? fileContent.charAt(idx) != '"' : fileContent.charAt(idx) != ' ' && fileContent.charAt(idx) != '\t') &&
               fileContent.charAt(idx) != '\n' &&
               fileContent.charAt(idx) != '\r') {
          configDir.append(fileContent.charAt(idx));
          idx++;
        }
      }
      if (configDir.length() > 0) {
        return Paths.get(fixDirName(configDir.toString())).toString();
      }
    }

    return null;
  }

  private static String fixDirName(String dir) {
    return FileUtil.expandUserHome(StringUtil.unquoteString(dir, '"'));
  }

  private static void doImport(@NotNull Path oldConfigDir, @NotNull Path newConfigDir, @Nullable Path oldIdeHome, @NotNull Logger log) {
    if (oldConfigDir.equals(newConfigDir)) {
      log.info("New config directory is the same as the old one, no import needed.");
      return;
    }

    Path oldPluginsDir = oldConfigDir.resolve(PLUGINS);
    if (!Files.isDirectory(oldPluginsDir)) {
      oldPluginsDir = null;
      if (oldIdeHome != null) {
        oldPluginsDir = getSettingsPath(oldIdeHome, PathManager.PROPERTY_PLUGINS_PATH, ConfigImportHelper::defaultPluginsPath);
      }
      if (oldPluginsDir == null) {
        oldPluginsDir = oldConfigDir.getFileSystem().getPath(defaultPluginsPath(getNameWithVersion(oldConfigDir)));
        // temporary code; safe to remove after 2020.1 branch is created
        if (!Files.isDirectory(oldPluginsDir) && oldPluginsDir.toString().contains("2020.1")) {
          oldPluginsDir = oldConfigDir.getFileSystem().getPath(
            defaultPluginsPath(getNameWithVersion(oldConfigDir).replace("2020.1", "2019.3")).replace("2019.3", "2020.1"));
        }
      }
    }

    Path newPluginsDir = newConfigDir.getFileSystem().getPath(PathManager.getPluginsPath());

    try {
      log.info(String.format(
        "Importing configs: oldConfigDir=[%s], newConfigDir=[%s], oldIdeHome=[%s], oldPluginsDir=[%s], newPluginsDir=[%s]",
        oldConfigDir, newConfigDir, oldIdeHome, oldPluginsDir, newPluginsDir));
      doImport(oldConfigDir, newConfigDir, oldIdeHome, oldPluginsDir, newPluginsDir, new ConfigImportOptions(log));
    }
    catch (Exception e) {
      log.warn(e);
      String message = ApplicationBundle.message("error.unable.to.import.settings", e.getMessage());
      Main.showMessage(ApplicationBundle.message("title.settings.import.failed"), message, false);
    }
  }

  static class ConfigImportOptions {
    final Logger log;
    boolean headless;
    BuildNumber compatibleBuildNumber = null;
    MarketplaceRequests marketplaceRequests = null;
    Path bundledPluginPath = null;
    Map<PluginId, Set<String>> brokenPluginVersions = null;

    ConfigImportOptions(Logger log) {
      this.log = log;
    }
  }

  static void doImport(@NotNull Path oldConfigDir,
                       @NotNull Path newConfigDir,
                       @Nullable Path oldIdeHome,
                       @NotNull Path oldPluginsDir,
                       @NotNull Path newPluginsDir,
                       @NotNull ConfigImportOptions options) throws IOException {
    Logger log = options.log;
    if (Files.isRegularFile(oldConfigDir)) {
      new Decompressor.Zip(oldConfigDir.toFile()).extract(newConfigDir.toFile());
      return;
    }

    // copy everything except plugins
    // the filter prevents web token reuse and accidental overwrite of files already created by this instance (port/lock/tokens etc.)
    FileUtil.copyDir(oldConfigDir.toFile(), newConfigDir.toFile(), file -> !blockImport(file.toPath(), oldConfigDir, newConfigDir, oldPluginsDir));

    List<StartupActionScriptManager.ActionCommand> actionCommands = loadStartupActionScript(oldConfigDir, oldIdeHome, oldPluginsDir);

    // copy plugins, unless new plugin directory is not empty (the plugin manager will sort out incompatible ones)
    if (!Files.isDirectory(oldPluginsDir)) {
      log.info("non-existing plugins directory: " + oldPluginsDir);
    }
    else if (!isEmptyDirectory(newPluginsDir)) {
      log.info("non-empty plugins directory: " + newPluginsDir);
    }
    else {
      migratePlugins(oldPluginsDir, newPluginsDir, actionCommands, options);
    }

    if (SystemInfo.isMac && (PlatformUtils.isIntelliJ() || "AndroidStudio".equals(PlatformUtils.getPlatformPrefix()))) {
      setKeymapIfNeeded(oldConfigDir, newConfigDir, log);
    }

    // apply stale plugin updates
    StartupActionScriptManager.executeActionScriptCommands(actionCommands, oldPluginsDir.toFile(), newPluginsDir.toFile());
    updateVMOptions(newConfigDir, log);
  }

  private static @NotNull List<StartupActionScriptManager.ActionCommand> loadStartupActionScript(@NotNull Path oldConfigDir, @Nullable Path oldIdeHome, @NotNull Path oldPluginsDir)
    throws IOException {
    if (Files.isDirectory(oldPluginsDir)) {
      Path oldSystemDir = oldConfigDir.getParent().resolve(SYSTEM);
      if (!Files.isDirectory(oldSystemDir)) {
        oldSystemDir = null;
        if (oldIdeHome != null) {
          oldSystemDir = getSettingsPath(oldIdeHome, PathManager.PROPERTY_SYSTEM_PATH, ConfigImportHelper::defaultSystemPath);
        }
        if (oldSystemDir == null) {
          oldSystemDir = oldConfigDir.getFileSystem().getPath(defaultSystemPath(getNameWithVersion(oldConfigDir)));
        }
      }
      Path script = oldSystemDir.resolve(PLUGINS + '/' + StartupActionScriptManager.ACTION_SCRIPT_FILE);  // PathManager#getPluginTempPath
      if (Files.isRegularFile(script)) {
        return StartupActionScriptManager.loadActionScript(script);
      }
    }
    return Collections.emptyList();
  }

  private static void migratePlugins(@NotNull Path oldPluginsDir,
                                     @NotNull Path newPluginsDir,
                                     List<StartupActionScriptManager.ActionCommand> actionCommands,
                                     @NotNull ConfigImportOptions options) throws IOException {
    Logger log = options.log;
    try {
      List<IdeaPluginDescriptorImpl> pluginsToMigrate = new ArrayList<>();
      List<IdeaPluginDescriptorImpl> incompatiblePlugins = new ArrayList<>();
      List<PluginId> pendingUpdates = collectPendingPluginUpdates(actionCommands, options);
      PluginManagerCore.getDescriptorsToMigrate(oldPluginsDir,
                                                options.compatibleBuildNumber,
                                                options.bundledPluginPath,
                                                options.brokenPluginVersions,
                                                pluginsToMigrate, incompatiblePlugins);

      migratePlugins(newPluginsDir, pluginsToMigrate, pendingUpdates, log);

      if (!incompatiblePlugins.isEmpty()) {
        if (options.headless) {
          downloadUpdatesForIncompatiblePlugins(newPluginsDir, options, incompatiblePlugins, pendingUpdates, new EmptyProgressIndicator());
        }
        else {
          ConfigImportProgressDialog dialog = new ConfigImportProgressDialog();
          dialog.setModalityType(Dialog.ModalityType.TOOLKIT_MODAL);
          AppUIUtil.updateWindowIcon(dialog);

          SplashManager.executeWithHiddenSplash(dialog, () -> {
            new Thread(() -> {
              downloadUpdatesForIncompatiblePlugins(newPluginsDir, options, incompatiblePlugins, pendingUpdates, dialog.getIndicator());
              SwingUtilities.invokeLater(() -> dialog.setVisible(false));
            }, "Plugin migration downloader").start();

            dialog.setVisible(true);
          });
        }
        // Migrate plugins for which we couldn't download updates
        migratePlugins(newPluginsDir, incompatiblePlugins, pendingUpdates, log);
      }
    }
    catch (ExecutionException | InterruptedException e) {
      log.info("Error loading list of plugins from old dir, migrating entire plugin directory");
      FileUtil.copyDir(oldPluginsDir.toFile(), newPluginsDir.toFile());
    }
  }

  private static List<PluginId> collectPendingPluginUpdates(List<StartupActionScriptManager.ActionCommand> commands,
                                                            @NotNull ConfigImportOptions options) {
    List<PluginId> result = new ArrayList<>();
    for (StartupActionScriptManager.ActionCommand command : commands) {
      String source;
      if (command instanceof StartupActionScriptManager.CopyCommand) {
        source = ((StartupActionScriptManager.CopyCommand)command).getSource();
      }
      else if (command instanceof StartupActionScriptManager.UnzipCommand) {
        source = ((StartupActionScriptManager.UnzipCommand)command).getSource();
      }
      else {
        continue;
      }

      try {
        IdeaPluginDescriptorImpl descriptor = PluginDescriptorLoader.loadDescriptorFromArtifact(Paths.get(source), null);
        if (descriptor != null) {
          result.add(descriptor.getPluginId());
        }
        else {
          options.log.info("No plugin descriptor in pending update " + source);
        }
      }
      catch (IOException e) {
        options.log.info("Failed to load plugin descriptor from pending update " + source);
      }
    }
    return result;
  }

  private static void migratePlugins(@NotNull Path newPluginsDir,
                                     List<IdeaPluginDescriptorImpl> pluginsToMigrate,
                                     List<PluginId> idsToSkip,
                                     Logger log) throws IOException {
    for (IdeaPluginDescriptorImpl descriptor : pluginsToMigrate) {
      if (idsToSkip.contains(descriptor.getPluginId())) {
        log.info("Skipping migration of plugin " + descriptor.getPluginId() + " because there's a pending update for it");
        continue;
      }
      log.info("Migrating plugin " + descriptor.getPluginId() + " version " + descriptor.getVersion());
      File path = descriptor.getPath();
      if (path.isDirectory()) {
        FileUtil.copyDir(path, new File(newPluginsDir.toFile(), path.getName()));
      }
      else {
        FileUtil.copy(path, new File(newPluginsDir.toFile(), path.getName()));
      }
    }
  }

  private static void downloadUpdatesForIncompatiblePlugins(@NotNull Path newPluginsDir,
                                                            @NotNull ConfigImportOptions options,
                                                            List<IdeaPluginDescriptorImpl> incompatiblePlugins,
                                                            List<PluginId> pendingUpdates,
                                                            ProgressIndicator indicator) {
    Logger log = options.log;
    for (Iterator<IdeaPluginDescriptorImpl> iterator = incompatiblePlugins.iterator(); iterator.hasNext(); ) {
      IdeaPluginDescriptorImpl plugin = iterator.next();
      if (pendingUpdates.contains(plugin.getPluginId())) {
        log.info("Skipping download of compatible version for plugin with pending update: " + plugin.getPluginId());
        iterator.remove();
        continue;
      }

      try {
        PluginDownloader downloader = PluginDownloader.createDownloader(plugin);
        if (options.marketplaceRequests != null) {
          downloader.setMarketplaceRequests(options.marketplaceRequests);
        }
        if (downloader.prepareToInstallAndLoadDescriptor(indicator, false) != null) {
          PluginInstaller.unpackPlugin(downloader.getFile(), newPluginsDir.toFile().getPath());
          log.info("Downloaded and unpacked compatible version of plugin " + plugin.getPluginId());
          iterator.remove();
        }
        else if (isBrokenPlugin(plugin, options)) {
          iterator.remove();
        }
      }
      catch (ProcessCanceledException e) {
        log.info("Plugin download cancelled");
        break;
      }
      catch (IOException e) {
        log.info("Failed to download and install compatible version of " + plugin.getPluginId() + ": " + e.getMessage());
      }
    }
  }

  private static boolean isBrokenPlugin(IdeaPluginDescriptorImpl plugin, ConfigImportOptions options) {
    Map<PluginId, Set<String>> versions = options.brokenPluginVersions;
    if (versions != null) {
      return versions.get(plugin.getPluginId()).contains(plugin.getVersion());
    }
    return PluginManagerCore.isBrokenPlugin(plugin);
  }

  private static boolean isEmptyDirectory(Path newPluginsDir) {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(newPluginsDir)) {
      for (Path path : stream) {
        boolean hidden = SystemInfo.isWindows ? Files.readAttributes(path, DosFileAttributes.class).isHidden() : path.getFileName().startsWith(".");
        if (!hidden) {
          return false;
        }
      }
    }
    catch (IOException ignored) { }
    return true;
  }

  static void setKeymapIfNeeded(@NotNull Path oldConfigDir, @NotNull Path newConfigDir, @NotNull Logger log) {
    String nameWithVersion = getNameWithVersion(oldConfigDir);
    Matcher m = Pattern.compile("\\.?[^\\d]+(\\d+\\.\\d+)?").matcher(nameWithVersion);
    if (!m.matches() || VersionComparatorUtil.compare("2019.1", m.group(1)) < 0) {
      return;
    }

    Path keymapOptionFile = newConfigDir.resolve("options/keymap.xml");
    if (Files.exists(keymapOptionFile)) {
      return;
    }

    try {
      Files.createDirectories(keymapOptionFile.getParent());
      Files.write(keymapOptionFile, ("<application>\n" +
                                    "  <component name=\"KeymapManager\">\n" +
                                    "    <active_keymap name=\"Mac OS X\" />\n" +
                                    "  </component>\n" +
                                    "</application>").getBytes(StandardCharsets.UTF_8));
    }
    catch (IOException e) {
      log.error("Cannot set keymap", e);
    }
  }

  /* Fix VM options in the custom *.vmoptions file that won't work with the current IDE version. */
  private static void updateVMOptions(Path newConfigDir, Logger log) {
    Path vmOptionsFile = newConfigDir.resolve(VMOptions.getCustomVMOptionsFileName());
    if (Files.exists(vmOptionsFile)) {
      try {
        List<String> lines = Files.readAllLines(vmOptionsFile);
        boolean updated = false;
        for (ListIterator<String> i = lines.listIterator(); i.hasNext(); ) {
          String line = i.next().trim();
          if (line.equals("-XX:MaxJavaStackTraceDepth=-1")) {
            i.set("-XX:MaxJavaStackTraceDepth=10000"); updated = true;
          }
          else if (line.startsWith("-agentlib:yjpagent") || "-Xverify:none".equals(line) || "-noverify".equals(line)) {
            i.remove(); updated = true;
          }
        }
        if (updated) {
          Files.write(vmOptionsFile, lines);
        }
      }
      catch (IOException e) {
        log.warn("Failed to update custom VM options file " + vmOptionsFile, e);
      }
    }
  }

  private static boolean blockImport(Path path, Path oldConfig, Path newConfig, Path oldPluginsDir) {
    if (oldConfig.equals(path.getParent())) {
      String name = path.getFileName().toString();
      return "user.web.token".equals(name) ||
             name.startsWith("chrome-user-data") ||
             Files.exists(newConfig.resolve(path.getFileName())) ||
             path.startsWith(oldPluginsDir);
    }
    return false;
  }

  private static String defaultConfigPath(String selector) {
    return newOrUnknown(selector) ? PathManager.getDefaultConfigPathFor(selector) :
           SystemInfo.isMac ? SystemProperties.getUserHome() + "/Library/Preferences/" + selector
                            : SystemProperties.getUserHome() + "/." + selector + '/' + CONFIG;
  }

  private static String defaultPluginsPath(String selector) {
    return newOrUnknown(selector) ? PathManager.getDefaultPluginPathFor(selector) :
           SystemInfo.isMac ? SystemProperties.getUserHome() + "/Library/Application Support/" + selector
                            : SystemProperties.getUserHome() + "/." + selector + '/' + CONFIG + '/' + PLUGINS;
  }

  private static String defaultSystemPath(String selector) {
    return newOrUnknown(selector) ? PathManager.getDefaultSystemPathFor(selector) :
           SystemInfo.isMac ? SystemProperties.getUserHome() + "/Library/Caches/" + selector
                            : SystemProperties.getUserHome() + "/." + selector + '/' + SYSTEM;
  }

  private static boolean newOrUnknown(String selector) {
    Matcher m = SELECTOR_PATTERN.matcher(selector);
    return !m.matches() || "2020.1".compareTo(m.group(2)) <= 0;
  }
}