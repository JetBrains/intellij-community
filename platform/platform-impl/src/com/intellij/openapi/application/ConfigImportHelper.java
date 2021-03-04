// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.diagnostic.VMOptions;
import com.intellij.ide.BootstrapBundle;
import com.intellij.ide.actions.ImportSettingsFilenameFilter;
import com.intellij.ide.cloudConfig.CloudConfigProvider;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginDescriptorLoader;
import com.intellij.ide.plugins.PluginInstaller;
import com.intellij.ide.plugins.PluginManagerCore;
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
import com.intellij.openapi.util.*;
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
import org.jetbrains.annotations.ApiStatus;
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
import static com.intellij.ide.SpecialConfigFiles.*;
import static com.intellij.openapi.application.CustomConfigMigrationOption.readCustomConfigMigrationOptionAndRemoveMarkerFile;
import static com.intellij.openapi.application.ImportOldConfigsUsagesCollector.ImportOldConfigsState;
import static com.intellij.openapi.application.ImportOldConfigsUsagesCollector.ImportOldConfigsState.InitialImportScenario.*;
import static com.intellij.openapi.application.PathManager.OPTIONS_DIRECTORY;
import static com.intellij.openapi.util.Pair.pair;

@ApiStatus.Internal
public final class ConfigImportHelper {
  private static final String FIRST_SESSION_KEY = "intellij.first.ide.session";
  private static final String CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY = "intellij.config.imported.in.current.session";
  public static final String CONFIG_IMPORTED_FROM_OTHER_PRODUCT_KEY = "intellij.config.imported.from.other.product";

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

  public static final Pattern SELECTOR_PATTERN = Pattern.compile("\\.?([^\\d]+)(\\d+(?:\\.\\d+)?)");
  private static final String SHOW_IMPORT_CONFIG_DIALOG_PROPERTY = "idea.initially.ask.config";

  // constant is used instead of util method to ensure that ConfigImportHelper class is not loaded by StartupUtil
  public static final String CUSTOM_MARKER_FILE_NAME = "migrate.config";

  private ConfigImportHelper() { }

  public static void importConfigsTo(boolean veryFirstStartOnThisComputer,
                                     @NotNull Path newConfigDir,
                                     @NotNull List<String> args,
                                     @NotNull Logger log) {
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

    ConfigImportSettings settings = findCustomConfigImportSettings();

    String pathSelectorOfOtherIde = (settings != null ? settings.getProductToImportFrom(args) : null);
    ConfigDirsSearchResult guessedOldConfigDirs = findConfigDirectories(newConfigDir, pathSelectorOfOtherIde);
    File tempBackup = null;
    boolean vmOptionFileChanged = false;
    ImportOldConfigsState.InitialImportScenario importScenarioStatistics = null;

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
            importScenarioStatistics = IMPORT_SETTINGS_ACTION;
          }
          else {
            tempBackup = backupCurrentConfigToTempAndDelete(newConfigDir, log, true);
            importScenarioStatistics = RESTORE_DEFAULT_ACTION;
          }
        }
        catch (IOException e) {
          log.error("Couldn't backup current config or delete current config directory", e);
        }
      }
      else if (shouldAskForConfig()) {
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
          oldConfigDirAndOldIdePath = showDialogAndGetOldConfigPath(guessedOldConfigDirs.getPaths());
          importScenarioStatistics = SHOW_DIALOG_CONFIGS_ARE_TOO_OLD;
        }
        else {
          oldConfigDirAndOldIdePath = findConfigDirectoryByPath(bestConfigGuess.first);

          if (oldConfigDirAndOldIdePath == null) {
            log.info("Previous config directory was detected but not accepted: " + bestConfigGuess.first);
            importScenarioStatistics = CONFIG_DIRECTORY_NOT_FOUND;
          }
        }
      }

      if (oldConfigDirAndOldIdePath != null) {
        Path oldConfigDir = oldConfigDirAndOldIdePath.first;
        Path oldIdeHome = oldConfigDirAndOldIdePath.second;

        ConfigImportOptions configImportOptions = new ConfigImportOptions(log);
        if (!guessedOldConfigDirs.fromSameProduct) {
          // Don't import plugins from other product even if configs are imported
          configImportOptions.importPlugins = false;
          System.setProperty(CONFIG_IMPORTED_FROM_OTHER_PRODUCT_KEY, oldConfigDir.getFileName().toString());
          importScenarioStatistics = IMPORTED_FROM_OTHER_PRODUCT;
        }
        else if (importScenarioStatistics == null) {
          importScenarioStatistics = IMPORTED_FROM_PREVIOUS_VERSION;
        }
        doImport(oldConfigDir, newConfigDir, oldIdeHome, log, configImportOptions);

        if (settings != null) {
          settings.importFinished(newConfigDir);
        }

        setConfigImportedInThisSession();
      }
      else {
        log.info("No configs imported, starting with clean configs at " + newConfigDir);
        if (importScenarioStatistics == null) {
          importScenarioStatistics = CLEAN_CONFIGS;
        }
      }

      ImportOldConfigsState.getInstance().reportImportScenario(importScenarioStatistics);
      vmOptionFileChanged |= doesVmOptionFileExist(newConfigDir);
    }
    finally {
      if (tempBackup != null) {
        try {
          moveTempBackupToStandardBackup(tempBackup);
        }
        catch (IOException e) {
          log.warn(String.format("Couldn't move the backup of current config from temp dir [%s] to backup dir", tempBackup), e);
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

      if (settings == null || settings.shouldRestartAfterVmOptionsChange()) {
        new CustomConfigMigrationOption.SetProperties(properties).writeConfigMarkerFile();
        restart();
      }
    }
  }

  static @Nullable ConfigImportSettings findCustomConfigImportSettings() {
    try {
      String customProviderName = "com.intellij.openapi.application." + PlatformUtils.getPlatformPrefix() + "ConfigImportSettings";
      @SuppressWarnings("unchecked") Class<ConfigImportSettings> customProviderClass = (Class<ConfigImportSettings>)Class.forName(customProviderName);
      if (ConfigImportSettings.class.isAssignableFrom(customProviderClass)) {
        return ReflectionUtil.newInstance(customProviderClass);
      }
    }
    catch (Exception ignored) { }
    return null;
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
        Main.showMessage(BootstrapBundle.message("restart.failed.title"), e);
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

  @NotNull
  private static File backupCurrentConfigToTempAndDelete(@NotNull Path currentConfig, @NotNull Logger log, boolean smartDelete) throws IOException {
    File tempBackupDir = FileUtil.createTempDirectory(getConfigDirName(), "-backup-" + UUID.randomUUID());
    log.info("Backup config from " + currentConfig + " to " + tempBackupDir);
    FileUtil.copyDir(PathManager.getConfigDir().toFile(), tempBackupDir, file -> !shouldSkipFileDuringImport(file.getName()));

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

  private static void moveTempBackupToStandardBackup(@NotNull File backupToMove) throws IOException {
    new ConfigBackup(PathManager.getConfigDir()).moveToBackup(backupToMove);
  }

  @NotNull
  static String getConfigDirName() {
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
  private static Pair<Path, Path> showDialogAndGetOldConfigPath(@NotNull List<Path> guessedOldConfigDirs) {
    ImportOldConfigsPanel dialog = new ImportOldConfigsPanel(guessedOldConfigDirs,
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

  /**
   * Checks that current user is "new", i. e. this is the first launch of the IDE on this machine.
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
    return ContainerUtil.exists(OPTIONS, name -> Files.exists(candidate.resolve(name)));
  }

  static class ConfigDirsSearchResult {
    private final @NotNull List<? extends Pair<Path, FileTime>> directories;
    private final boolean fromSameProduct;

    ConfigDirsSearchResult(@NotNull List<? extends Pair<Path, FileTime>> directories, boolean fromSameProduct) {
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
  }

  static @NotNull ConfigDirsSearchResult findConfigDirectories(@NotNull Path newConfigDir) {
    return findConfigDirectories(newConfigDir, null);
  }

  static @NotNull ConfigDirsSearchResult findConfigDirectories(@NotNull Path newConfigDir, @Nullable String productPrefixOtherIde) {
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

    List<Path> exactCandidates = new ArrayList<>();
    List<Path> otherPreferredCandidates = new ArrayList<>();
    List<Path> otherCandidates = new ArrayList<>();
    for (Path home : homes) {
      if (home == null || !Files.isDirectory(home)) continue;

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(home)) {
        for (Path path : stream) {
          if (!path.equals(newConfigDir) && Files.isDirectory(path)) {
            String name = path.getFileName().toString();
            if (nameMatchesPrefix(name, prefix)) {
              exactCandidates.add(path);
            }
            else if (exactCandidates.isEmpty() && productPrefixOtherIde != null) {
              if (nameMatchesPrefix(name, productPrefixOtherIde)) {
                otherPreferredCandidates.add(path);
              }
              else if (otherPreferredCandidates.isEmpty() && isConfigDirectory(path)) {
                otherCandidates.add(path);
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
    else if (!otherCandidates.isEmpty()) {
      candidates = otherCandidates;
      exact = false;
    }
    else {
      return new ConfigDirsSearchResult(Collections.emptyList(), true);
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

      lastModified.add(Pair.create(candidate, max != null ? max : FileTime.fromMillis(0)));
    }

    lastModified.sort((o1, o2) -> {
      int diff = o2.second.compareTo(o1.second);
      if (diff == 0) {
        diff = StringUtil.naturalCompare(o2.first.toString(), o1.first.toString());
      }
      return diff;
    });
    return new ConfigDirsSearchResult(lastModified, exact);
  }

  private static boolean nameMatchesPrefix(@NotNull String name, @NotNull String prefix) {
    return StringUtil.startsWithIgnoreCase(name, prefix) || StringUtil.startsWithIgnoreCase(name, '.' + prefix);
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

  private static @Nullable Path getSettingsPath(Path ideHome, String propertyName, Function<? super String, String> pathBySelector) {
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
      String fileContent = Files.readString(file);

      String fileName = file.getFileName().toString();
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

  private static void doImport(@NotNull Path oldConfigDir,
                               @NotNull Path newConfigDir,
                               @Nullable Path oldIdeHome,
                               @NotNull Logger log,
                               @NotNull ConfigImportOptions configImportOptions) {
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
      doImport(oldConfigDir, newConfigDir, oldIdeHome, oldPluginsDir, newPluginsDir, configImportOptions);
    }
    catch (Exception e) {
      log.warn(e);
      String message = BootstrapBundle.message("import.settings.failed", e.getMessage());
      Main.showMessage(BootstrapBundle.message("import.settings.failed.title"), message, false);
    }
  }

  static class ConfigImportOptions {
    final Logger log;
    boolean headless;
    boolean importPlugins = true;
    BuildNumber compatibleBuildNumber = null;
    ThrowableNotNullBiFunction<? super String, ? super ProgressIndicator, ? extends File, ? extends IOException> downloadFunction = null;
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
      new Decompressor.Zip(oldConfigDir.toFile()).extract(newConfigDir);
      return;
    }

    // copy everything except plugins
    // the filter prevents web token reuse and accidental overwrite of files already created by this instance (port/lock/tokens etc.)
    FileUtil.copyDir(oldConfigDir.toFile(), newConfigDir.toFile(), file -> !blockImport(file.toPath(), oldConfigDir, newConfigDir, oldPluginsDir));

    List<StartupActionScriptManager.ActionCommand> actionCommands = loadStartupActionScript(oldConfigDir, oldIdeHome, oldPluginsDir);

    // copy plugins, unless new plugin directory is not empty (the plugin manager will sort out incompatible ones)
    if (!options.importPlugins) {
      log.info("plugins are not imported.");
    }
    else if (!Files.isDirectory(oldPluginsDir)) {
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
    StartupActionScriptManager.executeActionScriptCommands(actionCommands, oldPluginsDir, newPluginsDir);
    updateVMOptions(newConfigDir, log);
  }

  private static @NotNull List<StartupActionScriptManager.ActionCommand> loadStartupActionScript(Path oldConfigDir,
                                                                                                 @Nullable Path oldIdeHome,
                                                                                                 Path oldPluginsDir) throws IOException {
    if (!Files.isDirectory(oldPluginsDir)) {
      return Collections.emptyList();
    }

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
    return StartupActionScriptManager.loadActionScript(script);
  }

  private static void migratePlugins(Path oldPluginsDir,
                                     Path newPluginsDir,
                                     List<? extends StartupActionScriptManager.ActionCommand> actionCommands,
                                     ConfigImportOptions options) throws IOException {
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

  private static List<PluginId> collectPendingPluginUpdates(List<? extends StartupActionScriptManager.ActionCommand> commands,
                                                            ConfigImportOptions options) {
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

  private static void migratePlugins(Path newPluginsDir,
                                     List<IdeaPluginDescriptorImpl> pluginsToMigrate,
                                     List<PluginId> idsToSkip,
                                     Logger log) throws IOException {
    for (IdeaPluginDescriptorImpl descriptor : pluginsToMigrate) {
      if (idsToSkip.contains(descriptor.getPluginId())) {
        log.info("Skipping migration of plugin " + descriptor.getPluginId() + " because there's a pending update for it");
        continue;
      }
      log.info("Migrating plugin " + descriptor.getPluginId() + " version " + descriptor.getVersion());
      File path = descriptor.getPluginPath().toFile();
      if (path.isDirectory()) {
        FileUtil.copyDir(path, new File(newPluginsDir.toFile(), path.getName()));
      }
      else {
        FileUtil.copy(path, new File(newPluginsDir.toFile(), path.getName()));
      }
    }
  }

  private static void downloadUpdatesForIncompatiblePlugins(Path newPluginsDir,
                                                            ConfigImportOptions options,
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
        if (options.downloadFunction != null) {
          downloader.setDownloadFunction(options.downloadFunction);
        }
        if (downloader.prepareToInstallAndLoadDescriptor(indicator, false) != null) {
          PluginInstaller.unpackPlugin(downloader.getFile().toPath(), newPluginsDir);
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
    return versions != null ? versions.get(plugin.getPluginId()).contains(plugin.getVersion()) : PluginManagerCore.isBrokenPlugin(plugin);
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
          else if ("-Xverify:none".equals(line) || "-noverify".equals(line) ||
                   line.startsWith("-agentlib:yjpagent") ||
                   line.startsWith("-agentpath:") && line.contains("yjpagent")) {
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
      return shouldSkipFileDuringImport(name) ||
             Files.exists(newConfig.resolve(path.getFileName())) ||
             path.startsWith(oldPluginsDir);
    }
    return false;
  }

  private static boolean shouldSkipFileDuringImport(@NotNull String fileName) {
    List<String> filesToSkip = Arrays.asList(PORT_FILE, PORT_LOCK_FILE, TOKEN_FILE, USER_WEB_TOKEN);
    return filesToSkip.contains(fileName) || fileName.startsWith(CHROME_USER_DATA);
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
