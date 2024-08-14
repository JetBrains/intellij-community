// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application;

import com.intellij.configurationStore.StoreUtilKt;
import com.intellij.diagnostic.VMOptions;
import com.intellij.ide.BootstrapBundle;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.actions.ImportSettingsFilenameFilter;
import com.intellij.ide.cloudConfig.CloudConfigProvider;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.plugins.*;
import com.intellij.ide.plugins.marketplace.MarketplacePluginDownloadService;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.ide.startup.StartupActionScriptManager.ActionCommand;
import com.intellij.ide.ui.laf.LookAndFeelThemeAdapterKt;
import com.intellij.idea.AppMode;
import com.intellij.openapi.application.migrations.AIAssistant241;
import com.intellij.openapi.application.migrations.JpaBuddyMigration242;
import com.intellij.openapi.application.migrations.NotebooksMigration242;
import com.intellij.openapi.application.migrations.PythonProMigration242;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.keymap.impl.KeymapManagerImpl;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.util.BuildNumber;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.NioFiles;
import com.intellij.openapi.util.registry.EarlyAccessRegistryManager;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ide.bootstrap.IdeStartupWizardKt;
import com.intellij.platform.ide.bootstrap.StartupErrorReporter;
import com.intellij.ui.AppUIUtilKt;
import com.intellij.util.PlatformUtils;
import com.intellij.util.Restarter;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.Decompressor;
import com.intellij.util.text.VersionComparatorUtil;
import com.intellij.util.ui.IoErrorText;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static com.intellij.ide.SpecialConfigFiles.*;
import static com.intellij.ide.plugins.BundledPluginsStateKt.BUNDLED_PLUGINS_FILENAME;
import static com.intellij.openapi.application.ImportOldConfigsState.InitialImportScenario.*;
import static com.intellij.openapi.application.migrations.AIAssistant241Kt.AI_PLUGIN_ID;
import static com.intellij.openapi.application.migrations.AIAssistant241Kt.migrateAiForToolbox;
import static com.intellij.openapi.application.migrations.Localization242Kt.enableL10nIfPluginInstalled;
import static com.intellij.openapi.application.migrations.PluginMigrationKt.MIGRATION_INSTALLED_PLUGINS_TXT;
import static com.intellij.platform.ide.bootstrap.SplashManagerKt.hideSplash;

@ApiStatus.Internal
public final class ConfigImportHelper {
  public static final String CONFIG_IMPORTED_FROM_OTHER_PRODUCT_KEY = "intellij.config.imported.from.other.product";
  public static final String CONFIG_IMPORTED_FROM_PREVIOUS_VERSION_KEY = "intellij.config.imported.from.previous.version";
  public static final Pattern SELECTOR_PATTERN = Pattern.compile("\\.?(\\D+)(\\d+(?:\\.\\d+)*)");
  public static final String CUSTOM_MARKER_FILE_NAME = "migrate.config";

  private static final String FIRST_SESSION_KEY = "intellij.first.ide.session";
  private static final String CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY = "intellij.config.imported.in.current.session";
  private static final String SHOW_IMPORT_CONFIG_DIALOG_PROPERTY = "idea.initially.ask.config";

  private static final String CONFIG = "config";
  private static final String[] OPTIONS = {
    PathManager.OPTIONS_DIRECTORY + '/' + StoragePathMacros.NON_ROAMABLE_FILE,
    PathManager.OPTIONS_DIRECTORY + '/' + GeneralSettings.IDE_GENERAL_XML,
    PathManager.OPTIONS_DIRECTORY + "/options.xml"};
  private static final String BIN = "bin";
  private static final String CONTENTS = "Contents";
  private static final String PLIST = "Info.plist";
  private static final String PLUGINS = "plugins";
  private static final String SYSTEM = "system";
  private static final Set<String> SESSION_FILES = Set.of(CUSTOM_MARKER_FILE_NAME, LOCK_FILE, PORT_LOCK_FILE, TOKEN_FILE, USER_WEB_TOKEN);

  private ConfigImportHelper() { }

  public static void importConfigsTo(boolean veryFirstStartOnThisComputer,
                                     @NotNull Path newConfigDir,
                                     @NotNull List<String> args,
                                     @NotNull Logger log) {
    log.info("Importing configs to " + newConfigDir);
    System.setProperty(FIRST_SESSION_KEY, Boolean.TRUE.toString());

    var customMigrationOption = CustomConfigMigrationOption.readCustomConfigMigrationOptionAndRemoveMarkerFile(newConfigDir);
    log.info("Custom migration option: " + customMigrationOption);

    if (customMigrationOption instanceof CustomConfigMigrationOption.SetProperties sp) {
      List<String> properties = sp.getProperties();
      log.info("Enabling system properties after restart: " + properties);
      for (String property : properties) System.setProperty(property, Boolean.TRUE.toString());
      return;
    }

    ConfigImportSettings settings = findCustomConfigImportSettings();
    log.info("Custom ConfigImportSettings instance: " + settings);

    List<String> otherProductPrefixes = settings != null ? settings.getProductsToImportFrom(args) : List.of();
    ConfigDirsSearchResult guessedOldConfigDirs = findConfigDirectories(newConfigDir, settings, otherProductPrefixes);
    Path tempBackup = null;
    boolean vmOptionFileChanged = false;
    @Nullable List<String> vmOptionsLines = null;
    ImportOldConfigsState.InitialImportScenario importScenarioStatistics = null;

    try {
      Pair<Path, Path> oldConfigDirAndOldIdePath = null;
      if (customMigrationOption instanceof CustomConfigMigrationOption.MigrateFromCustomPlace ||
          customMigrationOption instanceof CustomConfigMigrationOption.StartWithCleanConfig) {
        vmOptionFileChanged = doesVmOptionsFileExist(newConfigDir);
        try {
          if (customMigrationOption instanceof CustomConfigMigrationOption.MigrateFromCustomPlace mcp) {
            oldConfigDirAndOldIdePath = findConfigDirectoryByPath(mcp.getLocation());
            if (oldConfigDirAndOldIdePath == null) {
              logInfoAboutNotAcceptedConfigDirectory(log, "Custom location", mcp.getLocation());
            }
            if (oldConfigDirAndOldIdePath != null && oldConfigDirAndOldIdePath.first != null) {
              if (doesVmOptionsFileExist(newConfigDir) && !vmOptionsRequiresMerge(oldConfigDirAndOldIdePath.first, newConfigDir, log)) {
                //save old lines for the new file
                vmOptionsLines = Files.readAllLines(newConfigDir.resolve(VMOptions.getFileName()), VMOptions.getFileCharset());
                vmOptionFileChanged = false;
              }
            }
            tempBackup = backupCurrentConfigToTempAndDelete(newConfigDir, log, false, settings);
            importScenarioStatistics = IMPORT_SETTINGS_ACTION;
          }
          else {
            tempBackup = backupCurrentConfigToTempAndDelete(newConfigDir, log, true, settings);
            importScenarioStatistics = RESTORE_DEFAULT_ACTION;
          }
        }
        catch (IOException e) {
          log.error("Couldn't backup current config or delete current config directory", e);
        }
      }
      else if (IdeStartupWizardKt.isIdeStartupWizardEnabled()) {
        if (!guessedOldConfigDirs.isEmpty() && !shouldAskForConfig()) {
          Pair<Path, FileTime> bestConfigGuess = guessedOldConfigDirs.getFirstItem();
          if (!isConfigOld(bestConfigGuess.second)) {
            oldConfigDirAndOldIdePath = findConfigDirectoryByPath(bestConfigGuess.first);
            if (oldConfigDirAndOldIdePath == null) {
              logInfoAboutNotAcceptedConfigDirectory(log, "Previous config directory", bestConfigGuess.first);
              importScenarioStatistics = CONFIG_DIRECTORY_NOT_FOUND;
            }
          }
        }
      }
      else {
        boolean askForConfig = shouldAskForConfig();
        if (askForConfig) {
          oldConfigDirAndOldIdePath = showDialogAndGetOldConfigPath(guessedOldConfigDirs.getPaths());
          importScenarioStatistics = SHOW_DIALOG_REQUESTED_BY_PROPERTY;
        }
        else if (guessedOldConfigDirs.isEmpty()) {
          boolean importedFromCloud = false;
          CloudConfigProvider configProvider = CloudConfigProvider.getProvider();
          if (configProvider != null) {
            importedFromCloud = configProvider.importSettingsSilently(newConfigDir);

            if (importedFromCloud) {
              importScenarioStatistics = IMPORTED_FROM_CLOUD;
            }
          }
          if (!importedFromCloud && !veryFirstStartOnThisComputer) {
            oldConfigDirAndOldIdePath = showDialogAndGetOldConfigPath(guessedOldConfigDirs.getPaths());
            importScenarioStatistics = SHOW_DIALOG_NO_CONFIGS_FOUND;
          }
        }
        else {
          Pair<Path, FileTime> bestConfigGuess = guessedOldConfigDirs.getFirstItem();
          if (isConfigOld(bestConfigGuess.second)) {
            log.info("The best config guess [" + bestConfigGuess.first + "] is too old, it won't be used for importing.");
            oldConfigDirAndOldIdePath = showDialogAndGetOldConfigPath(guessedOldConfigDirs.getPaths());
            importScenarioStatistics = SHOW_DIALOG_CONFIGS_ARE_TOO_OLD;
          }
          else {
            oldConfigDirAndOldIdePath = findConfigDirectoryByPath(bestConfigGuess.first);
            if (oldConfigDirAndOldIdePath == null) {
              logInfoAboutNotAcceptedConfigDirectory(log, "Previous config directory", bestConfigGuess.first);
              importScenarioStatistics = CONFIG_DIRECTORY_NOT_FOUND;
            }
          }
        }
      }
      Path oldConfigDir;
      if (oldConfigDirAndOldIdePath != null) {
        oldConfigDir = oldConfigDirAndOldIdePath.first;
        Path oldIdeHome = oldConfigDirAndOldIdePath.second;

        var configImportOptions = new ConfigImportOptions(log);
        configImportOptions.importSettings = settings;
        configImportOptions.mergeVmOptions = customMigrationOption instanceof CustomConfigMigrationOption.MergeConfigs;
        
        /* in remote dev host mode UI cannot be shown before `Application` is initialized because it replaces the standard `awt.toolkit` 
           with com.intellij.platform.impl.toolkit.IdeToolkit which depends on `Application` */
        configImportOptions.setHeadless(AppMode.isRemoteDevHost());
        
        if (!guessedOldConfigDirs.fromSameProduct) {
          importScenarioStatistics = IMPORTED_FROM_OTHER_PRODUCT;
        }
        else if (importScenarioStatistics == null) {
          importScenarioStatistics = IMPORTED_FROM_PREVIOUS_VERSION;
        }

        System.setProperty(
          guessedOldConfigDirs.fromSameProduct ? CONFIG_IMPORTED_FROM_PREVIOUS_VERSION_KEY : CONFIG_IMPORTED_FROM_OTHER_PRODUCT_KEY,
          oldConfigDir.toString());

        doImport(oldConfigDir, newConfigDir, oldIdeHome, log, configImportOptions);

        setConfigImportedInThisSession();
      }
      else {
        oldConfigDir = null;
        log.info("No configs imported, starting with clean configs at " + newConfigDir);
        if (importScenarioStatistics == null) {
          importScenarioStatistics = CLEAN_CONFIGS;
        }
      }

      if (settings != null) {
        settings.importFinished(newConfigDir, otherProductPrefixes);
      }

      ImportOldConfigsState.Companion.getInstance().reportImportScenario(importScenarioStatistics);
      if (importScenarioStatistics == IMPORT_SETTINGS_ACTION && vmOptionsLines != null) {
        Path vmOptionsFile = newConfigDir.resolve(VMOptions.getFileName());
        try {
          Files.write(vmOptionsFile, vmOptionsLines, VMOptions.getFileCharset());
          vmOptionFileChanged = false;
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      } else {
        vmOptionFileChanged |= doesVmOptionsFileExist(newConfigDir);
      }
    }
    finally {
      if (tempBackup != null) {
        try {
          moveTempBackupToStandardBackup(newConfigDir, tempBackup);
        }
        catch (IOException e) {
          log.warn(String.format("Couldn't move the backup of current config from temp dir [%s] to backup dir", tempBackup), e);
        }
      }
    }

    // TODO remove hack, should we support vmoptions import in per project?
    // TODO If so, we need to patch restarter.
    if (vmOptionFileChanged && !ProjectManagerEx.IS_PER_PROJECT_INSTANCE_ENABLED) {
      log.info("The vmoptions file has changed, restarting...");
      if (settings == null || settings.shouldRestartAfterVmOptionsChange()) {
        writeOptionsForRestart(newConfigDir, log);
        restart(args);
      }
    }
  }

  private static void logInfoAboutNotAcceptedConfigDirectory(@NotNull Logger log, @NotNull String description, @NotNull Path path) {
    StringBuilder builder = new StringBuilder();
    builder.append(description + " was detected but not accepted: ");
    builder.append(path);
    builder.append(". Its content:\n");
    if (Files.isDirectory(path)) {
      try {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
          for (Path child : stream) {
            builder.append(" ").append(child.getFileName()).append("\n");
            if (Files.isDirectory(child)) {
              try (DirectoryStream<Path> grandChildren = Files.newDirectoryStream(path)) {
                for (Path grandChild : grandChildren) {
                  builder.append(" |- ").append(grandChild.getFileName()).append("\n");
                }
              }
            }
          }
        }
      }
      catch (IOException e) {
        builder.append("failed to get: ").append(e.toString());
      }
    }
    else {
      builder.append("not a directory");
    }
    log.info(builder.toString());
  }

  static @Nullable ConfigImportSettings findCustomConfigImportSettings() {
    try {
      String customProviderName = "com.intellij.openapi.application." + PlatformUtils.getPlatformPrefix() + "ConfigImportSettings";
      @SuppressWarnings("unchecked")
      Class<ConfigImportSettings> customProviderClass = (Class<ConfigImportSettings>)Class.forName(customProviderName);
      if (ConfigImportSettings.class.isAssignableFrom(customProviderClass)) {
        Constructor<ConfigImportSettings> constructor = customProviderClass.getDeclaredConstructor();
        try {
          constructor.setAccessible(true);
        }
        catch (SecurityException ignored) { }
        return constructor.newInstance();
      }
    }
    catch (Exception ignored) { }
    return null;
  }

  public static boolean isConfigOld(FileTime time) {
    Instant deadline = Instant.now().minus(180, ChronoUnit.DAYS);
    return time.toInstant().compareTo(deadline) < 0;
  }

  private static boolean doesVmOptionsFileExist(Path configDir) {
    return Files.isRegularFile(configDir.resolve(VMOptions.getFileName()));
  }

  public static void writeOptionsForRestartIfNeeded(@NotNull Logger log) {
    if (isFirstSession() && isConfigImported()) {
      writeOptionsForRestart(PathManager.getConfigDir(), log);
    }
  }

  private static void writeOptionsForRestart(@NotNull Path newConfigDir, @NotNull Logger log) {
    List<String> properties = new ArrayList<>();
    properties.add(FIRST_SESSION_KEY);
    if (isConfigImported()) {
      properties.add(CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY);
    }

    try {
      new CustomConfigMigrationOption.SetProperties(properties).writeConfigMarkerFile(newConfigDir);
    }
    catch (IOException e) {
      log.error("cannot write config migration marker file to " + newConfigDir, e);
    }
  }

  private static void restart(List<String> args) {
    if (Restarter.isSupported()) {
      try {
        Restarter.setMainAppArgs(args);
        Restarter.scheduleRestart(false);
      }
      catch (IOException e) {
        StartupErrorReporter.showError(BootstrapBundle.message("restart.failed.title"), e);
      }
      System.exit(0);
    }
    else {
      String title = BootstrapBundle.message("import.settings.title", ApplicationNamesInfo.getInstance().getFullProductName());
      String message = BootstrapBundle.message("import.settings.restart");
      String yes = BootstrapBundle.message("import.settings.restart.now"), no = BootstrapBundle.message("import.settings.restart.later");
      if (Messages.showYesNoDialog(message, title, yes, no, Messages.getQuestionIcon()) == Messages.YES) {
        System.exit(0);
      }
    }
  }

  private static Path backupCurrentConfigToTempAndDelete(Path currentConfig, Logger log, boolean smartDelete, @Nullable ConfigImportSettings settings) throws IOException {
    Path tempBackupDir = Files.createTempDirectory(currentConfig.getFileName() + "-backup-" + UUID.randomUUID());
    log.info("Backup config from " + currentConfig + " to " + tempBackupDir);
    FileUtil.copyDir(currentConfig.toFile(), tempBackupDir.toFile(), file -> !shouldSkipFileDuringImport(file.toPath(), settings));

    deleteCurrentConfigDir(currentConfig, log, smartDelete);

    Path pluginDir = currentConfig.getFileSystem().getPath(PathManager.getPluginsPath());
    if (Files.exists(pluginDir) && !pluginDir.startsWith(currentConfig)) {
      Path pluginBackup = tempBackupDir.resolve(PLUGINS);
      log.info("Backup plugins dir separately from " + pluginDir + " to " + pluginBackup);
      NioFiles.createDirectories(pluginBackup);
      FileUtil.copyDir(pluginDir.toFile(), pluginBackup.toFile());
      NioFiles.deleteRecursively(pluginDir);
    }

    return tempBackupDir;
  }

  private static void deleteCurrentConfigDir(Path currentConfig, Logger log, boolean smartDelete) throws IOException {
    log.debug("Removing current config directory, smartDelete: " + smartDelete);
    if (!smartDelete) {
      NioFiles.deleteRecursively(currentConfig);
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
      NioFiles.deleteRecursively(currentConfig);
    }
  }

  private static void moveTempBackupToStandardBackup(Path newConfigDir, Path backupToMove) throws IOException {
    new ConfigBackup(newConfigDir).moveToBackup(backupToMove);
  }

  private static boolean shouldAskForConfig() {
    String showImportDialog = System.getProperty(SHOW_IMPORT_CONFIG_DIALOG_PROPERTY);
    if ("default-production".equals(showImportDialog) || "never".equals(showImportDialog)) {
      return false;
    }
    return PluginManagerCore.isRunningFromSources() ||
           System.getProperty(PathManager.PROPERTY_CONFIG_PATH) != null ||
           "true".equals(showImportDialog);
  }

  private static @Nullable Pair<Path, Path> showDialogAndGetOldConfigPath(List<Path> guessedOldConfigDirs) {
    String showImportDialog = System.getProperty(SHOW_IMPORT_CONFIG_DIALOG_PROPERTY);
    if ("never".equals(showImportDialog)) {
      return null;
    }

    //noinspection TestOnlyProblems
    LookAndFeelThemeAdapterKt.setEarlyUiLaF();

    ImportOldConfigsPanel dialog = new ImportOldConfigsPanel(guessedOldConfigDirs, ConfigImportHelper::findConfigDirectoryByPath);
    dialog.setModalityType(Dialog.ModalityType.TOOLKIT_MODAL);
    AppUIUtilKt.updateAppWindowIcon(dialog);

    hideSplash();
    dialog.setVisible(true);
    var result = dialog.getSelectedFile();
    dialog.dispose();
    return result;
  }

  /** Returns {@code true} when the IDE is launched for the first time (i.e., there was no config directory). */
  public static boolean isFirstSession() {
    return Boolean.getBoolean(FIRST_SESSION_KEY);
  }

  /**
   * Checking that the current user is a "new" one (i.e., this is the very first launch of the IDE on this machine).
   */
  public static boolean isNewUser() {
    return isFirstSession() && !isConfigImported();
  }

  /** Simple check by file type, content is not checked. */
  public static boolean isSettingsFile(@NotNull VirtualFile file) {
    return FileTypeRegistry.getInstance().isFileOfType(file, ArchiveFileType.INSTANCE);
  }

  public static void setConfigImportedInThisSession() {
    System.setProperty(CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY, Boolean.TRUE.toString());
  }

  /**
   * Returns {@code true} when the IDE is launched for the first time, and configs were imported from another installation.
   */
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
    for (String t : OPTIONS) {
      if (Files.exists(candidate.resolve(t))) {
        return true;
      }
    }
    return false;
  }

  static final class ConfigDirsSearchResult {
    private final List<? extends Pair<Path, FileTime>> directories;
    private final boolean fromSameProduct;

    private ConfigDirsSearchResult(List<? extends Pair<Path, FileTime>> directories, boolean fromSameProduct) {
      this.directories = directories;
      this.fromSameProduct = fromSameProduct;
    }

    @NotNull List<Path> getPaths() {
      return ContainerUtil.map(directories, it -> it.first);
    }

    boolean isEmpty() {
      return directories.isEmpty();
    }

    @NotNull Pair<Path, FileTime> getFirstItem() {
      return directories.get(0);
    }

    @NotNull @NlsSafe String getNameAndVersion(@NotNull Path config) {
      return getNameWithVersion(config);
    }

    @NotNull List<Path> findRelatedDirectories(@NotNull Path config, boolean forAutoClean) {
      return getRelatedDirectories(config, forAutoClean);
    }
  }

  @ApiStatus.Internal
  public static boolean hasPreviousVersionConfigDirs() {
    ConfigDirsSearchResult directories = findConfigDirectories(PathManager.getConfigDir());
    return directories.fromSameProduct && directories.directories.size() > 1;
  }

  static @NotNull ConfigDirsSearchResult findConfigDirectories(@NotNull Path newConfigDir) {
    return findConfigDirectories(newConfigDir, null, List.of());
  }

  static @NotNull ConfigDirsSearchResult findConfigDirectories(@NotNull Path newConfigDir,
                                                               @Nullable ConfigImportSettings settings,
                                                               @NotNull List<String> otherProductPrefixes) {
    // looking for existing config directories ...
    Set<Path> homes = new HashSet<>();
    homes.add(newConfigDir.getParent());  // ... in the vicinity of the new config directory
    homes.add(newConfigDir.getFileSystem().getPath(PathManager.getDefaultConfigPathFor("X")).getParent());  // ... in the default location
    Path historic = newConfigDir.getFileSystem().getPath(defaultConfigPath("X2019.3"));
    Path historicHome = SystemInfoRt.isMac ? historic.getParent() : historic.getParent().getParent();
    homes.add(historicHome);  // ... in the historic location

    String prefix = getPrefixFromSelector(PathManager.getPathsSelector());
    if (prefix == null) prefix = getPrefixFromSelector(getNameWithVersion(newConfigDir));
    if (prefix == null) {
      String productName = ApplicationNamesInfo.getInstance().getFullProductName();
      if (productName != null) prefix = productName.replace(" ", "");
    }
    if (prefix == null) prefix = PlatformUtils.getPlatformPrefix();

    List<Path> exactCandidates = new ArrayList<>();
    List<Path> otherPreferredCandidates = new ArrayList<>();
    for (Path home : homes) {
      if (home == null || !Files.isDirectory(home)) {
        continue;
      }

      if (home.equals(newConfigDir.getParent()) &&
          ProjectManagerEx.IS_PER_PROJECT_INSTANCE_ENABLED &&
          ProjectManagerEx.Companion.isChildProcessPath(newConfigDir)) {
        exactCandidates.add(home);
        break;
      }

      boolean dotted = !SystemInfoRt.isMac && home == historicHome;

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(home)) {
        for (Path path : stream) {
          if (!path.equals(newConfigDir) && Files.isDirectory(path)) {
            String name = path.getFileName().toString();
            String pathPrefix = getPrefixFromSelector(getNameWithVersion(path));
            if (nameMatchesPrefixStrictly(name, prefix, dotted)) {
              if (settings == null || settings.shouldBeSeenAsImportCandidate(path, pathPrefix, otherProductPrefixes)) {
                exactCandidates.add(path);
              }
            }
            else if (exactCandidates.isEmpty() &&
                     ContainerUtil.exists(otherProductPrefixes, other -> nameMatchesPrefixStrictly(name, other, dotted))) {
              if (settings == null || settings.shouldBeSeenAsImportCandidate(path, pathPrefix, otherProductPrefixes)) {
                otherPreferredCandidates.add(path);
              }
            }
          }
        }
      }
      catch (IOException ignore) { }
    }

    List<Path> candidates;
    boolean exact;
    if (!exactCandidates.isEmpty()) {
      candidates = exactCandidates;
      exact = true;
    }
    else if (!otherPreferredCandidates.isEmpty()) {
      candidates = otherPreferredCandidates;
      exact = false;
    }
    else {
      return new ConfigDirsSearchResult(List.of(), true);
    }

    List<Pair<Path, FileTime>> lastModified = new ArrayList<>();
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

      lastModified.add(new Pair<>(candidate, max != null ? max : FileTime.fromMillis(0)));
    }

    lastModified.sort((o1, o2) -> {
      int diff = o2.second.compareTo(o1.second);
      if (diff == 0) {
        diff = NaturalComparator.INSTANCE.compare(o2.first.toString(), o1.first.toString());
      }
      return diff;
    });

    return new ConfigDirsSearchResult(lastModified, exact);
  }

  private static boolean nameMatchesPrefixStrictly(String name, String prefix, boolean dotted) {
    String strictPrefix = dotted ? '.' + prefix : prefix;
    return StringUtilRt.startsWithIgnoreCase(name, strictPrefix) &&
           name.length() > strictPrefix.length() &&
           Character.isDigit(name.charAt(strictPrefix.length()));
  }

  private static String getNameWithVersion(Path configDir) {
    String name = configDir.getFileName().toString();
    if (CONFIG.equals(name)) {
      name = Strings.trimStart(configDir.getParent().getFileName().toString(), ".");
    }
    return name;
  }

  private static @Nullable String parseVersionFromConfig(@NotNull Path configDir) {
    String nameWithVersion = getNameWithVersion(configDir);
    Matcher m = matchNameWithVersion(nameWithVersion);
    String version = null;
    if (m.matches()) {
      version = m.group(1);
    }
    return version;
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

  public static @Nullable Pair<Path, Path> findConfigDirectoryByPath(Path selectedDir) {
    // tries to map a user selection into a valid config directory
    // returns a pair of a config directory and an IDE home (when a user pointed to it; null otherwise)

    if (isConfigDirectory(selectedDir)) {
      return new Pair<>(selectedDir, null);
    }

    Path config = selectedDir.resolve(CONFIG);
    if (isConfigDirectory(config)) {
      return new Pair<>(config, null);
    }

    if (Files.isDirectory(selectedDir.resolve(SystemInfoRt.isMac ? CONTENTS : BIN))) {
      Path configDir = getSettingsPath(selectedDir, PathManager.PROPERTY_CONFIG_PATH, ConfigImportHelper::defaultConfigPath);
      if (configDir != null && isConfigDirectory(configDir)) {
        return new Pair<>(configDir, selectedDir);
      }
    }

    return null;
  }

  private static @Nullable Path getSettingsPath(Path ideHome, String propertyName, Function<? super String, String> pathBySelector) {
    List<Path> files = new ArrayList<>();
    if (SystemInfoRt.isMac) {
      files.add(ideHome.resolve(CONTENTS + '/' + BIN + '/' + PathManager.PROPERTIES_FILE_NAME));
      files.add(ideHome.resolve(CONTENTS + '/' + PLIST));
    }
    else {
      files.add(ideHome.resolve(BIN + '/' + PathManager.PROPERTIES_FILE_NAME));
      String scriptName = ApplicationNamesInfo.getInstance().getScriptName();
      files.add(ideHome.resolve(BIN + '/' + scriptName + ".bat"));
      files.add(ideHome.resolve(BIN + '/' + scriptName + ".sh"));
    }

    // an explicitly specified directory
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
      String fileContent = Files.readString(file);

      String fileName = file.getFileName().toString();
      if (fileName.endsWith(".properties")) {
        PropertyResourceBundle bundle = new PropertyResourceBundle(new StringReader(fileContent));
        return bundle.containsKey(propertyName) ? bundle.getString(propertyName) : null;
      }

      if (fileName.endsWith(".plist")) {
        String propertyValue = findPListKey(propertyName, fileContent);
        if (!Strings.isEmpty(propertyValue)) {
          return propertyValue;
        }
      }

      String propertyValue = findProperty(propertyName, fileContent);
      if (!Strings.isEmpty(propertyValue)) {
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
      if (!configDir.isEmpty()) {
        return Path.of(fixDirName(configDir.toString())).toString();
      }
    }

    return null;
  }

  private static String fixDirName(String dir) {
    return FileUtil.expandUserHome(StringUtilRt.unquoteString(dir, '"'));
  }

  public static void doImport(Path oldConfigDir, Path newConfigDir, @Nullable Path oldIdeHome, Logger log, ConfigImportOptions importOptions) {
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
      }
    }

    Path newPluginsDir = newConfigDir.getFileSystem().getPath(PathManager.getPluginsPath());

    try {
      log.info(String.format(
        "Importing configs: oldConfigDir=[%s], newConfigDir=[%s], oldIdeHome=[%s], oldPluginsDir=[%s], newPluginsDir=[%s]",
        oldConfigDir, newConfigDir, oldIdeHome, oldPluginsDir, newPluginsDir));
      doImport(oldConfigDir, newConfigDir, oldIdeHome, oldPluginsDir, newPluginsDir, importOptions);
    }
    catch (Exception e) {
      log.warn(e);
      String message = BootstrapBundle.message("import.settings.failed", IoErrorText.message(e));
      StartupErrorReporter.showWarning(BootstrapBundle.message("import.settings.failed.title"), message);
    }
  }

  public static final class ConfigImportOptions {
    final Logger log;
    boolean headless;
    @Nullable ConfigImportSettings importSettings;
    BuildNumber compatibleBuildNumber;
    MarketplacePluginDownloadService downloadService;
    Path bundledPluginPath = null;
    @Nullable Map<PluginId, Set<String>> brokenPluginVersions = null;
    boolean mergeVmOptions = false;

    @Nullable
    ProgressIndicator headlessProgressIndicator = null;

    public ConfigImportOptions(Logger log) {
      this.log = log;
    }

    public boolean isHeadless() {
      return headless;
    }

    public void setHeadless(boolean headless) {
      this.headless = headless;
    }

    public boolean isMergeVmOptions() {
      return mergeVmOptions;
    }

    public void setMergeVmOptions(boolean mergeVmOptions) {
      this.mergeVmOptions = mergeVmOptions;
    }

    @Nullable
    public ProgressIndicator getHeadlessProgressIndicator() {
      return headlessProgressIndicator;
    }

    public void setHeadlessProgressIndicator(@Nullable ProgressIndicator headlessProgressIndicator) {
      this.headlessProgressIndicator = headlessProgressIndicator;
    }

    public ConfigImportSettings getImportSettings() {
      return importSettings;
    }

    public void setImportSettings(ConfigImportSettings importSettings) {
      this.importSettings = importSettings;
    }
  }

  @VisibleForTesting
  static void doImport(@NotNull Path oldConfigDir,
                       @NotNull Path newConfigDir,
                       @Nullable Path oldIdeHome,
                       @NotNull Path oldPluginsDir,
                       @NotNull Path newPluginsDir,
                       @NotNull ConfigImportOptions options) throws IOException {
    Logger log = options.log;
    if (Files.isRegularFile(oldConfigDir)) {
      new Decompressor.Zip(oldConfigDir).extract(newConfigDir);
      return;
    }

    // Copy everything except plugins.
    // The filter prevents web token reuse and accidental overwrite of files already created by this instance (port/lock/tokens etc.).
    Files.walkFileTree(oldConfigDir, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        return blockImport(dir, oldConfigDir, newConfigDir, oldPluginsDir, options.importSettings) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Path target = newConfigDir.resolve(oldConfigDir.relativize(file));
        if (options.mergeVmOptions && file.getFileName().toString().equals(VMOptions.getFileName()) && Files.exists(target)) {
          mergeVmOptions(file, target, options.log);
        }
        else if (!blockImport(file, oldConfigDir, newConfigDir, oldPluginsDir, options.importSettings)) {
          NioFiles.createDirectories(target.getParent());
          Files.copy(file, target, LinkOption.NOFOLLOW_LINKS);
        }
        else if (overwriteOnImport(file)) {
          NioFiles.createDirectories(target.getParent());
          Files.copy(file, target, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING);
        }
        return FileVisitResult.CONTINUE;
      }
    });

    List<ActionCommand> actionCommands = loadStartupActionScript(oldConfigDir, oldIdeHome, oldPluginsDir);

    // copying plugins, unless the target directory is not empty (the plugin manager will sort out incompatible ones)
    if (!isEmptyDirectory(newPluginsDir)) {
      log.info("non-empty plugins directory: " + newPluginsDir);

      // ad-hoc migration for AI Assistant in Toolbox
      var pluginsToDownload = new ArrayList<IdeaPluginDescriptor>();
      var previousVersion = parseVersionFromConfig(oldConfigDir);
      migrateAiForToolbox(newPluginsDir, newConfigDir, previousVersion, log, pluginsToDownload);
      if (!pluginsToDownload.isEmpty()) {
        downloadUpdatesForIncompatiblePlugins(newPluginsDir, options, pluginsToDownload);
        writeMigrationResult(newConfigDir, AI_PLUGIN_ID, log);
      }
    }
    else {
      Predicate<IdeaPluginDescriptor> hasPendingUpdate =
        Files.isDirectory(oldPluginsDir) ? collectPendingPluginUpdates(actionCommands, options.log) : __ -> false;
      migratePlugins(oldPluginsDir, oldConfigDir, newPluginsDir, newConfigDir, options, hasPendingUpdate);
    }

    migrateLocalization(oldConfigDir, oldPluginsDir);

    if (SystemInfoRt.isMac && (PlatformUtils.isIntelliJ() || "AndroidStudio".equals(PlatformUtils.getPlatformPrefix()))) {
      setKeymapIfNeeded(oldConfigDir, newConfigDir, log);
    }

    // applying prepared updates to copied plugins
    StartupActionScriptManager.executeActionScriptCommands(actionCommands, oldPluginsDir, newPluginsDir);

    updateVMOptions(newConfigDir, log);
  }

  public static void migrateLocalization(Path oldConfigDir, Path oldPluginsDir) {
    enableL10nIfPluginInstalled(parseVersionFromConfig(oldConfigDir), oldPluginsDir);
  }

  private static List<ActionCommand> loadStartupActionScript(Path oldConfigDir, @Nullable Path oldIdeHome, Path oldPluginsDir) throws IOException {
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
    return List.of();
  }

  public static void migratePlugins(Path oldPluginsDir,
                                    Path oldConfigDir,
                                    Path newPluginsDir,
                                    Path newConfigDir,
                                    ConfigImportOptions options,
                                    Predicate<IdeaPluginDescriptor> hasPendingUpdate) throws IOException {
    Logger log = options.log;

    List<IdeaPluginDescriptor> pluginsToMigrate = new ArrayList<>();
    List<IdeaPluginDescriptor> pluginsToDownload = new ArrayList<>();

    try {
      Map<PluginId, Set<String>> brokenPluginVersions = options.brokenPluginVersions;
      PluginLoadingResult result = PluginDescriptorLoader.loadDescriptorsFromOtherIde(
        oldPluginsDir, options.bundledPluginPath, brokenPluginVersions, options.compatibleBuildNumber);

      partitionNonBundled(result.getIdMap().values(), pluginsToDownload, pluginsToMigrate, descriptor -> {
        Set<String> brokenVersions = brokenPluginVersions != null ? brokenPluginVersions.get(descriptor.getPluginId()) : null;
        return brokenVersions != null && brokenVersions.contains(descriptor.getVersion());
      });

      partitionNonBundled(result.getIncompleteIdMap().values(), pluginsToDownload, pluginsToMigrate, __ -> true);
    }
    catch (ExecutionException | InterruptedException e) {
      log.info("Error loading list of plugins from old dir, migrating entire plugin directory");
      FileUtil.copyDir(oldPluginsDir.toFile(), newPluginsDir.toFile());
      return;
    }
    catch (IOException e) {
      log.info("Non-existing plugins directory: " + oldPluginsDir, e);
    }
    Path disabledPluginsFile = oldConfigDir.resolve(DisabledPluginsState.DISABLED_PLUGINS_FILENAME);
    Set<PluginId> disabledPlugins =
      Files.exists(disabledPluginsFile) ? DisabledPluginsState.Companion.loadDisabledPlugins(disabledPluginsFile) : Set.of();
    for (IdeaPluginDescriptor pluginToMigrate : pluginsToMigrate) {
      if (disabledPlugins.contains(pluginToMigrate.getPluginId())) {
        pluginToMigrate.setEnabled(false);
      }
    }
    if (options.importSettings != null) {
      options.importSettings.processPluginsToMigrate(newConfigDir, oldConfigDir, pluginsToMigrate, pluginsToDownload);
    }

    migrateGlobalPlugins(newConfigDir, oldConfigDir, pluginsToMigrate, pluginsToDownload, options.log);

    pluginsToMigrate.removeIf(hasPendingUpdate);
    if (!pluginsToMigrate.isEmpty()) {
      migratePlugins(newPluginsDir, pluginsToMigrate, log);
    }

    pluginsToDownload.removeIf(hasPendingUpdate);
    if (!pluginsToDownload.isEmpty()) {
      downloadUpdatesForIncompatiblePlugins(newPluginsDir, options, pluginsToDownload);

      // migrating plugins for which we weren't able to download updates
      migratePlugins(newPluginsDir, pluginsToDownload, log);
    }
  }

  private static void performMigrations(PluginMigrationOptions options) {
    // WRITE IN MIGRATIONS HERE

    new AIAssistant241().migratePlugins(options);
    new PythonProMigration242().migratePlugins(options);
    new NotebooksMigration242().migratePlugins(options);
    new JpaBuddyMigration242().migratePlugins(options);
  }

  private static void migrateGlobalPlugins(Path newConfigDir,
                                           Path oldConfigDir,
                                           List<IdeaPluginDescriptor> toMigrate,
                                           List<IdeaPluginDescriptor> toDownload,
                                           Logger log) {
    var currentProductVersion = PluginManagerCore.getBuildNumber().asStringWithoutProductCode();
    String previousVersion = parseVersionFromConfig(oldConfigDir);

    var options = new PluginMigrationOptions(previousVersion, currentProductVersion,
                                             newConfigDir, oldConfigDir,
                                             toMigrate, toDownload,
                                             log);

    performMigrations(options);

    var downloadIds = toDownload.stream()
      .map(descriptor -> descriptor.getPluginId().getIdString())
      .collect(Collectors.joining("\n"));
    writeMigrationResult(newConfigDir, downloadIds, options.getLog());
  }

  private static void writeMigrationResult(Path newConfigDir, String downloadIds, Logger log) {
    var resultFile = newConfigDir.resolve(MIGRATION_INSTALLED_PLUGINS_TXT);

    try {
      Files.writeString(resultFile, downloadIds);
    }
    catch (IOException e) {
      log.error("Unable to write auto install result", e);
    }
  }

  private static void partitionNonBundled(Collection<? extends IdeaPluginDescriptor> descriptors,
                                          List<IdeaPluginDescriptor> firstAccumulator,
                                          List<IdeaPluginDescriptor> secondAccumulator,
                                          Predicate<IdeaPluginDescriptor> predicate) {
    for (var descriptor : descriptors) {
      if (!descriptor.isBundled()) {
        (predicate.test(descriptor) ? firstAccumulator : secondAccumulator).add(descriptor);
      }
    }
  }

  private static Predicate<IdeaPluginDescriptor> collectPendingPluginUpdates(List<ActionCommand> actionCommands, Logger log) {
    Set<PluginId> result = new LinkedHashSet<>();
    for (ActionCommand command : actionCommands) {
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
          log.info("No plugin descriptor in pending update: " + source);
        }
      }
      catch (IOException e) {
        log.info("Failed to load plugin descriptor from pending update: " + source);
      }
    }

    return descriptor -> {
      PluginId pluginId = descriptor.getPluginId();
      if (result.contains(pluginId)) {
        log.info("Plugin '" + pluginId + "' skipped due to a pending update");
        return true;
      }
      else {
        return false;
      }
    };
  }

  public static void migratePlugins(Path newPluginsDir, List<IdeaPluginDescriptor> descriptors, Logger log) throws IOException {
    for (IdeaPluginDescriptor descriptor : descriptors) {
      Path pluginPath = descriptor.getPluginPath();
      PluginId pluginId = descriptor.getPluginId();
      if (pluginPath == null) {
        log.info("Skipping migration of plugin '" + pluginId + "', because it is officially homeless");
        continue;
      }

      log.info("Migrating plugin '" + pluginId + "' version: " + descriptor.getVersion());
      Path target = newPluginsDir.resolve(pluginPath.getFileName());
      if (Files.isDirectory(pluginPath)) {
        FileUtil.copyDir(pluginPath.toFile(), target.toFile());
      }
      else {
        Files.createDirectories(newPluginsDir);
        Files.copy(pluginPath, target);
      }
    }
  }

  private static void downloadUpdatesForIncompatiblePlugins(Path newPluginsDir,  ConfigImportOptions options, List<IdeaPluginDescriptor> incompatiblePlugins) {
    if (options.headless) {
      PluginDownloader.runSynchronouslyInBackground(() -> {
        ProgressIndicator progressIndicator =
          options.headlessProgressIndicator == null ? new EmptyProgressIndicator(ModalityState.nonModal()) : options.headlessProgressIndicator;
        downloadUpdatesForIncompatiblePlugins(newPluginsDir, options, incompatiblePlugins, progressIndicator);
      });
    }
    else {
      ThreadingAssertions.assertEventDispatchThread();

      ConfigImportProgressDialog dialog = new ConfigImportProgressDialog();
      dialog.setModalityType(Dialog.ModalityType.TOOLKIT_MODAL);
      AppUIUtilKt.updateAppWindowIcon(dialog);
      hideSplash();
      PluginDownloader.runSynchronouslyInBackground(() -> {
        try {
          downloadUpdatesForIncompatiblePlugins(newPluginsDir, options, incompatiblePlugins, dialog.getIndicator());
        }
        catch (Throwable e) {
          options.log.info("Failed to download updates for plugins", e);
        }
        SwingUtilities.invokeLater(() -> dialog.setVisible(false));
      });
      dialog.setVisible(true);
    }
  }

  private static void downloadUpdatesForIncompatiblePlugins(Path newPluginsDir,
                                                            ConfigImportOptions options,
                                                            List<IdeaPluginDescriptor> incompatiblePlugins,
                                                            ProgressIndicator indicator) {
    ThreadingAssertions.assertBackgroundThread();
    Logger log = options.log;
    for (Iterator<IdeaPluginDescriptor> iterator = incompatiblePlugins.iterator(); iterator.hasNext(); ) {
      IdeaPluginDescriptor descriptor = iterator.next();
      PluginId pluginId = descriptor.getPluginId();

      try {
        PluginDownloader downloader = PluginDownloader.createDownloader(descriptor)
          .withErrorsConsumer(__ -> {})
          .withDownloadService(options.downloadService);

        if (downloader.prepareToInstall(indicator)) {
          PluginInstaller.unpackPlugin(downloader.getFilePath(), newPluginsDir);
          log.info("Downloaded and unpacked compatible version of plugin '" + pluginId + "'");
          iterator.remove();
        }
        else if (isBrokenPlugin(descriptor, options.brokenPluginVersions)) {
          iterator.remove();
        }
      }
      catch (ProcessCanceledException e) {
        log.info("Plugin download cancelled");
        break;
      }
      catch (IOException e) {
        log.info("Failed to download and install compatible version of '" + pluginId + "': " + e.getMessage());
      }
    }
  }

  private static boolean isBrokenPlugin(IdeaPluginDescriptor descriptor, @Nullable Map<PluginId, Set<String>> brokenPluginVersions) {
    if (brokenPluginVersions == null) {
      return BrokenPluginFileKt.isBrokenPlugin(descriptor);
    }
    Set<String> versions = brokenPluginVersions.get(descriptor.getPluginId());
    return versions != null && versions.contains(descriptor.getVersion());
  }

  private static boolean isEmptyDirectory(Path newPluginsDir) {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(newPluginsDir)) {
      for (Path path : stream) {
        boolean hidden = SystemInfoRt.isWindows
                         ? Files.readAttributes(path, DosFileAttributes.class).isHidden()
                         : path.getFileName().startsWith(".");
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
    Matcher m = matchNameWithVersion(nameWithVersion);
    if (m.matches() && VersionComparatorUtil.compare("2019.1", m.group(1)) >= 0) {
      String keymapFileSpec = StoreUtilKt.getDefaultStoragePathSpec(KeymapManagerImpl.class);
      if (keymapFileSpec != null) {
        Path keymapOptionFile = newConfigDir.resolve(PathManager.OPTIONS_DIRECTORY).resolve(keymapFileSpec);
        if (!Files.exists(keymapOptionFile)) {
          try {
            Files.createDirectories(keymapOptionFile.getParent());
            Files.writeString(keymapOptionFile, ("""
                                                   <application>
                                                     <component name="KeymapManager">
                                                       <active_keymap name="Mac OS X" />
                                                     </component>
                                                   </application>"""));
          }
          catch (IOException e) {
            log.error("Cannot set keymap", e);
          }
        }
      }
    }
  }

  private static @NotNull Matcher matchNameWithVersion(String nameWithVersion) {
    return Pattern.compile("\\.?\\D+(\\d+\\.\\d+)?").matcher(nameWithVersion);
  }

  /*
   * Merging imported VM option file with the one pre-created by an external tool (like the Toolbox app).
   * When both files set the same property, the value from an external tool is supposed to be more actual.
   * When both files set `-Xmx`, a higher value is preferred.
   */
  public static void mergeVmOptions(Path importFile, Path currentFile, Logger log) {
    try {
      var cs = VMOptions.getFileCharset();
      var importLines = Files.readAllLines(importFile, cs);
      var currentLines = Files.readAllLines(currentFile, cs);
      ArrayList<String> result = mergeVmOptionsLines(importLines, currentLines, log);
      Files.write(currentFile, result, cs);
    }
    catch (IOException e) {
      log.warn("Failed to merge VM option files " + importFile + " and " + currentFile, e);
    }
  }

  private static boolean vmOptionsRequiresMerge(@Nullable Path oldConfigDir, Path newConfigDir, Logger log){
    if (oldConfigDir == null) {
      return false;
    }
    if (newConfigDir == null) {
      return true;
    }
    Path importFile = oldConfigDir.resolve(VMOptions.getFileName());
    Path currentFile = newConfigDir.resolve(VMOptions.getFileName());
    try {
      if (!Files.isRegularFile(importFile))
        return false;
      if (!Files.isRegularFile(currentFile))
        return true;

      var cs = VMOptions.getFileCharset();
      var importLines = Files.readAllLines(importFile, cs);
      var currentLines = Files.readAllLines(currentFile, cs);
      currentLines.sort(String::compareTo);
      ArrayList<String> result = mergeVmOptionsLines(importLines, currentLines, log);
      updateVMOptionsLines(newConfigDir, result, log);
      result.sort(String::compareTo);
      return !currentLines.equals(result);
    }
    catch (IOException e) {
      log.warn("Failed to merge VM option files " + importFile + " and " + currentFile, e);
      return true;
    }
  }

  private static ArrayList<String> mergeVmOptionsLines(List<String> importLines, List<String> currentLines, Logger log) {
    var result = new ArrayList<String>(importLines.size() + currentLines.size());
    var preferCurrentXmx = false;

    nextLine:
    for (var line : importLines) {
      if (line.startsWith("-D")) {
        var p = line.indexOf('=');
        if (p > 0) {
          var prefix = line.substring(0, p + 1);
          for (var l : currentLines) {
            if (l.startsWith(prefix)) {
              continue nextLine;
            }
          }
        }
      }
      else if (line.startsWith("-Xmx") && isLowerValue("-Xmx", line.substring(4), currentLines)) {
        preferCurrentXmx = true;
        continue nextLine;
      }
      result.add(line);
    }

    for (var line : currentLines) {
      if (preferCurrentXmx || !line.startsWith("-Xmx")) {
        result.add(line);
      }
    }
    return result;
  }

  /* Fix VM options in the custom *.vmoptions file that won't work with the current IDE version or duplicate/undercut platform ones. */
  @SuppressWarnings("SpellCheckingInspection")
  public static void updateVMOptions(Path newConfigDir, Logger log) {
    Path vmOptionsFile = newConfigDir.resolve(VMOptions.getFileName());
    if (Files.exists(vmOptionsFile)) {
      try {
        List<String> lines = Files.readAllLines(vmOptionsFile, VMOptions.getFileCharset());
        boolean updated = updateVMOptionsLines(newConfigDir, lines, log);

        if (updated) {
          Files.write(vmOptionsFile, lines, VMOptions.getFileCharset());
        }
      }
      catch (IOException e) {
        log.warn("Failed to update custom VM options file " + vmOptionsFile, e);
      }
    }
  }

  private static boolean updateVMOptionsLines(Path newConfigDir, List<String> lines, Logger log) {
    Path platformVmOptionsFile = newConfigDir.getFileSystem().getPath(VMOptions.getPlatformOptionsFile().toString());
    Collection<String> platformLines = new LinkedHashSet<>(readPlatformOptions(platformVmOptionsFile, log));
    boolean updated = false;

    for (ListIterator<String> i = lines.listIterator(); i.hasNext(); ) {
      String line = i.next().trim();
      if (line.equals("-XX:MaxJavaStackTraceDepth=-1")) {
        i.set("-XX:MaxJavaStackTraceDepth=10000"); updated = true;
      }
      else if ("-XX:+UseConcMarkSweepGC".equals(line) ||
               "-Xverify:none".equals(line) || "-noverify".equals(line) ||
               "-XX:+UseCompressedOops".equals(line) ||
               line.startsWith("-agentlib:yjpagent") ||
               line.startsWith("-agentpath:") && line.contains("yjpagent") ||
               "-Dsun.io.useCanonPrefixCache=false".equals(line) ||
               "-Dfile.encoding=UTF-8".equals(line) && SystemInfoRt.isMac ||
               isDuplicateOrLowerValue(line, platformLines)) {
        i.remove(); updated = true;
      }
    }
    return updated;
  }

  @VisibleForTesting
  static List<String> readPlatformOptions(Path platformVmOptionsFile, Logger log) {
    try {
      return Files.readAllLines(platformVmOptionsFile, VMOptions.getFileCharset());
    }
    catch (IOException e) {
      // exceptions should not prevent a user's VM options file from being processed
      log.warn("Cannot read platform VM options file " + platformVmOptionsFile, e);
      return List.of();
    }
  }

  private static boolean isDuplicateOrLowerValue(String line, Collection<String> platformLines) {
    if (platformLines.isEmpty()) {
      return false;
    }
    if (platformLines.contains(line)) {
      return true;
    }
    if (line.startsWith("-Xms") || line.startsWith("-Xmx") || line.startsWith("-Xss")) {
      return isLowerValue(line.substring(0, 4), line.substring(4), platformLines);
    }
    if (line.startsWith("-XX:")) {
      int p = line.indexOf('=', 4);
      if (p > 0) return isLowerValue(line.substring(0, p + 1), line.substring(p + 1), platformLines);
    }
    return false;
  }

  private static boolean isLowerValue(String prefix, String userValue, Collection<String> platformLines) {
    for (String line : platformLines) {
      if (line.startsWith(prefix)) {
        try {
          return VMOptions.parseMemoryOption(userValue) <= VMOptions.parseMemoryOption(line.substring(prefix.length()));
        }
        catch (IllegalArgumentException ignored) { }
      }
    }
    return false;
  }

  private static boolean blockImport(Path path, Path oldConfig, Path newConfig, Path oldPluginsDir, @Nullable ConfigImportSettings settings) {
    if (ProjectManagerEx.Companion.isChildProcessPath(path)) return true;
    if (oldConfig.equals(path.getParent())) {
      Path fileName = path.getFileName();
      return shouldSkipFileDuringImport(path, settings) ||
             Files.exists(newConfig.resolve(fileName)) ||
             path.startsWith(oldPluginsDir);
    }
    if (settings != null && settings.shouldSkipPath(path)) {
      return true; // this check needs to repeat even for non-root paths
    }
    return false;
  }

  private static boolean shouldSkipFileDuringImport(Path path, @Nullable ConfigImportSettings settings) {
    String fileName = path.getFileName().toString();
    return SESSION_FILES.contains(fileName) ||
           fileName.equals(BUNDLED_PLUGINS_FILENAME) ||
           fileName.equals(StoragePathMacros.APP_INTERNAL_STATE_DB) ||
           fileName.equals(ExpiredPluginsState.EXPIRED_PLUGINS_FILENAME) ||
           fileName.startsWith(CHROME_USER_DATA) ||
           fileName.endsWith(".jdk") && fileName.startsWith(String.valueOf(ApplicationNamesInfo.getInstance().getScriptName())) ||
           (settings != null && settings.shouldSkipPath(path));
  }

  private static boolean overwriteOnImport(Path path) {
    return path.endsWith(EarlyAccessRegistryManager.fileName);
  }

  private static String defaultConfigPath(String selector) {
    return newOrUnknown(selector) ? PathManager.getDefaultConfigPathFor(selector) :
           SystemInfoRt.isMac ? SystemProperties.getUserHome() + "/Library/Preferences/" + selector
                            : SystemProperties.getUserHome() + "/." + selector + '/' + CONFIG;
  }

  private static String defaultPluginsPath(String selector) {
    return newOrUnknown(selector) ? PathManager.getDefaultPluginPathFor(selector) :
           SystemInfoRt.isMac ? SystemProperties.getUserHome() + "/Library/Application Support/" + selector
                            : SystemProperties.getUserHome() + "/." + selector + '/' + CONFIG + '/' + PLUGINS;
  }

  private static String defaultSystemPath(String selector) {
    return newOrUnknown(selector) ? PathManager.getDefaultSystemPathFor(selector) :
           SystemInfoRt.isMac ? SystemProperties.getUserHome() + "/Library/Caches/" + selector
                            : SystemProperties.getUserHome() + "/." + selector + '/' + SYSTEM;
  }

  private static String defaultLogsPath(String selector) {
    return newOrUnknown(selector) ? PathManager.getDefaultLogPathFor(selector) :
           SystemInfoRt.isMac ? SystemProperties.getUserHome() + "/Library/Logs/" + selector
                            : SystemProperties.getUserHome() + "/." + selector + '/' + SYSTEM + "/logs";
  }

  private static boolean newOrUnknown(String selector) {
    Matcher m = SELECTOR_PATTERN.matcher(selector);
    return !m.matches() || "2020.1".compareTo(m.group(2)) <= 0;
  }

  private static List<Path> getRelatedDirectories(Path config, boolean forAutoClean) {
    String selector = getNameWithVersion(config);
    FileSystem fs = config.getFileSystem();
    Path system = fs.getPath(defaultSystemPath(selector));

    if (!forAutoClean) {
      Path commonParent = config.getParent();
      if (commonParent.equals(system.getParent())) {
        List<Path> files = NioFiles.list(commonParent);
        if (files.size() == 1 || files.size() == 2 && files.containsAll(List.of(config, system))) {
          return List.of(commonParent);
        }
      }
    }

    List<Path> result = new ArrayList<>();

    if (!forAutoClean) {
      result.add(config);
    }

    if (Files.exists(system)) {
      result.add(system);
    }

    if (!forAutoClean) {
      Path plugins = fs.getPath(defaultPluginsPath(selector));
      if (!plugins.startsWith(config) && Files.exists(plugins)) {
        result.add(plugins);
      }
    }

    Path logs = fs.getPath(defaultLogsPath(selector));
    if (!logs.startsWith(system) && Files.exists(logs)) {
      result.add(logs);
    }

    return result;
  }
}
