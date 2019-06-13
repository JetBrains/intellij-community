// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.diagnostic.VMOptions;
import com.intellij.ide.actions.ImportSettingsFilenameFilter;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.idea.Main;
import com.intellij.idea.SplashManager;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.Decompressor;
import com.intellij.util.io.PathKt;
import com.intellij.util.text.VersionComparatorUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import static com.intellij.ide.GeneralSettings.IDE_GENERAL_XML;
import static com.intellij.openapi.application.PathManager.OPTIONS_DIRECTORY;
import static com.intellij.openapi.util.Pair.pair;
import static com.intellij.openapi.util.text.StringUtil.startsWithIgnoreCase;

public final class ConfigImportHelper {
  private static final String FIRST_SESSION_KEY = "intellij.first.ide.session";
  private static final String CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY = "intellij.config.imported.in.current.session";

  public static final String CONFIG = "config";
  private static final String[] OPTIONS = {
    OPTIONS_DIRECTORY + '/' + StoragePathMacros.NON_ROAMABLE_FILE,
    OPTIONS_DIRECTORY + '/' + IDE_GENERAL_XML,
    OPTIONS_DIRECTORY + "/options.xml"};
  private static final String BIN = "bin";
  private static final String CONTENTS = "Contents";
  private static final String PLIST = "Info.plist";
  private static final String PLUGINS = "plugins";

  private ConfigImportHelper() { }

  public static void importConfigsTo(@NotNull Path newConfigDir, @NotNull Logger log) {
    System.setProperty(FIRST_SESSION_KEY, Boolean.TRUE.toString());

    ConfigImportSettings settings = getConfigImportSettings();
    List<Path> guessedOldConfigDirs = findConfigDirectories(newConfigDir, SystemInfo.isMac, true);

    ImportOldConfigsPanel dialog = new ImportOldConfigsPanel(guessedOldConfigDirs, f -> findConfigDirectoryByPath(f));
    dialog.setModalityType(Dialog.ModalityType.TOOLKIT_MODAL);
    AppUIUtil.updateWindowIcon(dialog);

    Ref<Pair<Path, Path>> result = new Ref<>();
    SplashManager.executeWithHiddenSplash(dialog, () -> {
      dialog.setVisible(true);
      result.set(dialog.getSelectedFile());
      dialog.dispose();
    });

    if (!result.isNull()) {
      doImport(result.get().first, newConfigDir, result.get().second, log);
      if (settings != null) {
        settings.importFinished(newConfigDir);
      }
      System.setProperty(CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY, Boolean.TRUE.toString());
    }
  }

  /**
   * Returns {@code true} when the IDE is launched for the first time (i.e. there was no config directory).
   */
  public static boolean isFirstSession() {
    return Boolean.getBoolean(FIRST_SESSION_KEY);
  }

  /**
   * Simple check by file type, content is not checked.
   */
  public static boolean isSettingsFile(@NotNull VirtualFile file) {
    return FileTypeRegistry.getInstance().isFileOfType(file, ArchiveFileType.INSTANCE);
  }

  public static boolean isValidSettingsFile(@NotNull File file) {
    try (ZipFile zip = new ZipFile(file)) {
      return zip.getEntry(ImportSettingsFilenameFilter.SETTINGS_JAR_MARKER) != null;
    }
    catch (IOException ignored) {
      return false;
    }
  }

  /**
   * Returns {@code true} when the IDE is launched for the first time, and configs were imported from another installation.
   */
  public static boolean isConfigImported() {
    return Boolean.getBoolean(CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY);
  }

  public static boolean isConfigDirectory(@NotNull Path candidate) {
    for (String name : OPTIONS) {
      if (Files.exists(candidate.resolve(name))) return true;
    }
    return false;
  }

  @Nullable
  private static ConfigImportSettings getConfigImportSettings() {
    try {
      String customProviderName = "com.intellij.openapi.application." + PlatformUtils.getPlatformPrefix() + "ConfigImportSettings";
      @SuppressWarnings("unchecked") Class<ConfigImportSettings> customProviderClass = (Class<ConfigImportSettings>)Class.forName(customProviderName);
      if (ConfigImportSettings.class.isAssignableFrom(customProviderClass)) {
        return ReflectionUtil.newInstance(customProviderClass);
      }
    }
    catch (ClassNotFoundException | RuntimeException ignored) { }
    return null;
  }

  @NotNull
  public static List<Path> findConfigDirectories(@NotNull Path newConfigDir, boolean isMacOs, boolean checkDefaultLocation) {
    // looks for the most recent existing config directory in the vicinity of the new one, assuming standard layout
    // ("~/Library/<selector_prefix><selector_version>" on macOS, "~/.<selector_prefix><selector_version>/config" on other OSes)

    List<Path> homes = new ArrayList<>(2);
    homes.add((isMacOs ? newConfigDir : newConfigDir.getParent()).getParent());
    String nameWithSelector = StringUtil.notNullize(PathManager.getPathsSelector(), getNameWithVersion(newConfigDir, isMacOs));
    String prefix = getPrefixFromSelector(nameWithSelector, isMacOs);

    String defaultPrefix = StringUtil.replace(StringUtil.notNullize(
      ApplicationNamesInfo.getInstance().getFullProductName(), PlatformUtils.getPlatformPrefix()), " ", "");
    if (checkDefaultLocation) {
      Path configDir = Paths.get(PathManager.getDefaultConfigPathFor(defaultPrefix));
      Path configHome = (isMacOs ? configDir : configDir.getParent()).getParent();
      if (!homes.contains(configHome)) homes.add(configHome);
    }

    List<Path> candidates = new ArrayList<>();
    for (Path dir : homes) {
      if (dir == null || !Files.isDirectory(dir)) continue;

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, it -> {
        String fileName = it.getFileName().toString();
        if ((prefix == null || !startsWithIgnoreCase(fileName, prefix)) &&
            !startsWithIgnoreCase(fileName, defaultPrefix)) return false;
        if (!Files.isDirectory(it)) return false;
        return !it.equals(isMacOs ? newConfigDir : newConfigDir.getParent());
      })) {
        for (Path path : stream) {
          candidates.add(path);
        }
      }
      catch (IOException ignore) {
      }
    }

    if (candidates.isEmpty()) {
      return Collections.emptyList();
    }

    Map<Path, FileTime> lastModified = new THashMap<>();
    for (Path child : candidates) {
      Path candidate = isMacOs ? child : child.resolve(CONFIG);
      FileTime max = null;
      for (String name : OPTIONS) {
        try {
          FileTime cur = Files.getLastModifiedTime(candidate.resolve(name));
          if (max == null || cur.compareTo(max) > 0) {
            max = cur;
          }
        }
        catch (IOException ignore) {
        }
      }
      lastModified.put(candidate, max != null ? max : FileTime.fromMillis(0));
    }

    List<Path> result = new ArrayList<>(lastModified.keySet());
    result.sort((o1, o2) -> {
      int diff = lastModified.get(o2).compareTo(lastModified.get(o1));
      if (diff == 0) {
        diff = StringUtil.naturalCompare(o2.toString(), o1.toString());
      }
      return diff;
    });
    return result;
  }

  @NotNull
  private static String getNameWithVersion(@NotNull Path configDir, boolean isMacOs) {
    return (isMacOs ? configDir : configDir.getParent()).getFileName().toString();
  }

  @Nullable
  private static String getPrefixFromSelector(@NotNull String nameWithSelector, boolean isMacOs) {
    Matcher m = Pattern.compile("\\.?([^\\d]+)\\d+(\\.\\d+)?").matcher(nameWithSelector);
    String selector = m.matches() ? m.group(1) : null;
    return StringUtil.isEmpty(selector) ? null : isMacOs ? selector : '.' + selector;
  }

  @Nullable
  private static Pair<Path, Path> findConfigDirectoryByPath(@NotNull Path selectedDir) {
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
      Path configDir = getSettingsPath(selectedDir, PathManager.PROPERTY_CONFIG_PATH, PathManager::getDefaultConfigPathFor);
      if (configDir != null && isConfigDirectory(configDir)) {
        return pair(configDir, selectedDir);
      }
    }

    return null;
  }

  @Nullable
  private static Path getSettingsPath(@NotNull Path ideHome, String propertyName, Function<? super String, String> pathBySelector) {
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
          Path candidate = Paths.get(candidatePath);
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
          Path candidate = Paths.get(pathBySelector.apply(selector));
          if (Files.isDirectory(candidate)) {
            return candidate;
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private static String getPropertyFromFile(@NotNull Path file, String propertyName) {
    try {
      String fileContent = PathKt.readText(file);

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

  @Nullable
  private static String findPListKey(String propertyName, String fileContent) {
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

  @Nullable
  private static String findProperty(String propertyName, String fileContent) {
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
      configDir = new StringBuilder(fixDirName(configDir.toString()));
      if (configDir.length() > 0) {
        configDir = new StringBuilder(new File(configDir.toString()).getPath());
      }
      return configDir.toString();
    }

    return null;
  }

  private static String fixDirName(String dir) {
    return FileUtil.expandUserHome(StringUtil.unquoteString(dir, '"'));
  }

  private static void doImport(@NotNull Path oldConfigDir, @NotNull Path newConfigDir, @Nullable Path oldIdeHome, @NotNull Logger log) {
    if (oldConfigDir.equals(newConfigDir)) {
      return;
    }

    try {
      if (Files.isRegularFile(oldConfigDir)) {
        new Decompressor.Zip(oldConfigDir.toFile()).extract(newConfigDir.toFile());
        return;
      }

      // copy everything including plugins (the plugin manager will sort out incompatible ones)
      // the filter prevents web token reuse and accidental overwrite of files already created by this instance (port/lock/tokens etc.)
      FileUtil.copyDir(oldConfigDir.toFile(), newConfigDir.toFile(), path -> !blockImport(path.toPath(), oldConfigDir, newConfigDir));

      if (SystemInfo.isMac) {
        setKeymapIfNeeded(oldConfigDir, newConfigDir, log);
      }

      // on macOS, plugins are normally not under the config directory
      Path oldPluginsDir = oldConfigDir.resolve(PLUGINS);
      if (SystemInfo.isMac && !Files.isDirectory(oldPluginsDir)) {
        oldPluginsDir = null;
        if (oldIdeHome != null) {
          oldPluginsDir = getSettingsPath(oldIdeHome, PathManager.PROPERTY_PLUGINS_PATH, PathManager::getDefaultPluginPathFor);
        }
        if (oldPluginsDir == null) {
          oldPluginsDir = Paths.get(PathManager.getDefaultPluginPathFor(oldConfigDir.getFileName().toString()));
        }
        if (Files.isDirectory(oldPluginsDir)) {
          Path newPluginsDir = Paths.get(PathManager.getPluginsPath());
          FileUtil.copyDir(oldPluginsDir.toFile(), newPluginsDir.toFile());
        }
      }

      // apply stale plugin updates
      if (Files.isDirectory(oldPluginsDir)) {
        Path oldSystemDir = null;
        if (oldIdeHome != null) {
          oldSystemDir = getSettingsPath(oldIdeHome, PathManager.PROPERTY_SYSTEM_PATH, PathManager::getDefaultSystemPathFor);
        }
        if (oldSystemDir == null) {
          String selector = SystemInfo.isMac ? oldConfigDir.getFileName().toString() : StringUtil.trimLeading(oldConfigDir.getParent().getFileName().toString(), '.');
          oldSystemDir = Paths.get(PathManager.getDefaultSystemPathFor(selector));
        }
        Path script = oldSystemDir.resolve(PLUGINS + '/' + StartupActionScriptManager.ACTION_SCRIPT_FILE);  // PathManager#getPluginTempPath
        if (Files.isRegularFile(script)) {
          File newPluginsDir = new File(PathManager.getPluginsPath());
          StartupActionScriptManager.executeActionScript(script, oldPluginsDir, newPluginsDir);
        }
      }

      updateVMOptions(newConfigDir, log);
    }
    catch (IOException e) {
      log.warn(e);
      String message = ApplicationBundle.message("error.unable.to.import.settings", e.getMessage());
      Main.showMessage(ApplicationBundle.message("title.settings.import.failed"), message, false);
    }
  }

  public static void setKeymapIfNeeded(@NotNull Path oldConfigDir, @NotNull Path newConfigDir, @NotNull Logger log) {
    String nameWithVersion = getNameWithVersion(oldConfigDir, true);
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

  /**
   * Fix VM options in the custom *.vmoptions file which don't work with the current IDE version.
   */
  private static void updateVMOptions(@NotNull Path newConfigDir, @NotNull Logger log) {
    Path vmOptionsFile = newConfigDir.resolve(VMOptions.getCustomVMOptionsFileName());
    if (Files.exists(vmOptionsFile)) {
      try {
        List<String> lines = Files.readAllLines(vmOptionsFile);
        List<String> updatedLines = ContainerUtil.map(lines, ConfigImportHelper::replaceVMOptions);
        if (!updatedLines.equals(lines)) {
          PathKt.write(vmOptionsFile, StringUtil.join(updatedLines, "\n"));
        }
      }
      catch (IOException e) {
        log.warn("Failed to update custom VM options file " + vmOptionsFile, e);
      }
    }
  }

  private static String replaceVMOptions(String line) {
    line = line.trim().equals("-XX:MaxJavaStackTraceDepth=-1") ? "-XX:MaxJavaStackTraceDepth=10000" : line;
    return line.trim().startsWith("-agentlib:yjpagent") ? "" : line;
  }

  private static boolean blockImport(@NotNull Path path, Path oldConfig, Path newConfig) {
    return path.getParent() == oldConfig && ("user.web.token".equals(path.getFileName().toString()) || Files.exists(newConfig.resolve(path.getFileName())));
  }
}