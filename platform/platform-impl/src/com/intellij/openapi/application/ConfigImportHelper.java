// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.ui.Messages;
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
import com.intellij.util.io.Decompressor;
import com.intellij.util.text.VersionComparatorUtil;
import gnu.trove.THashMap;
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

    ConfigImportSettings settings = null;
    try {
      String customProviderName = "com.intellij.openapi.application." + PlatformUtils.getPlatformPrefix() + "ConfigImportSettings";
      @SuppressWarnings("unchecked") Class<ConfigImportSettings> customProviderClass = (Class<ConfigImportSettings>)Class.forName(customProviderName);
      if (ConfigImportSettings.class.isAssignableFrom(customProviderClass)) {
        settings = ReflectionUtil.newInstance(customProviderClass);
      }
    }
    catch (Exception ignored) { }

    List<Path> guessedOldConfigDirs = findConfigDirectories(newConfigDir);

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

      if (Files.isRegularFile(newConfigDir.resolve(VMOptions.getCustomVMOptionsFileName()))) {
        String title = ApplicationBundle.message("title.import.settings", ApplicationNamesInfo.getInstance().getFullProductName());
        String message = ApplicationBundle.message("restart.import.settings");
        String[] options = {ApplicationBundle.message("restart.import.now"), ApplicationBundle.message("restart.import.later")};
        if (JOptionPane.showOptionDialog(JOptionPane.getRootFrame(), message, title, JOptionPane.YES_NO_OPTION,
                                         JOptionPane.INFORMATION_MESSAGE, Messages.getInformationIcon(), options, null) == 0) {
          if (Restarter.isSupported()) {
            try {
              Restarter.scheduleRestart(false);
            }
            catch (IOException e) {
              Main.showMessage("Restart failed", e);
            }
          }
          System.exit(0);
        }
      }

      System.setProperty(CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY, Boolean.TRUE.toString());
    }
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

  static boolean isConfigDirectory(@NotNull Path candidate) {
    for (String name : OPTIONS) {
      if (Files.exists(candidate.resolve(name))) return true;
    }
    return false;
  }

  static @NotNull List<Path> findConfigDirectories(@NotNull Path newConfigDir) {
    // looks for existing config directories in the vicinity of the new one, assuming standard layout
    // ("~/Library/<selector_prefix><selector_version>" on macOS, "~/.<selector_prefix><selector_version>/config" on other OSes)
    boolean isMacOs = SystemInfo.isMac;

    List<Path> homes = new ArrayList<>(2);
    homes.add((isMacOs ? newConfigDir : newConfigDir.getParent()).getParent());
    String defaultPrefix = StringUtil.replace(StringUtil.notNullize(
      ApplicationNamesInfo.getInstance().getFullProductName(), PlatformUtils.getPlatformPrefix()), " ", "");
    Path configDir = newConfigDir.getFileSystem().getPath(PathManager.getDefaultConfigPathFor(defaultPrefix));
    Path configHome = (isMacOs ? configDir : configDir.getParent()).getParent();
    if (!homes.contains(configHome)) homes.add(configHome);

    String nameWithSelector = StringUtil.notNullize(PathManager.getPathsSelector(), getNameWithVersion(newConfigDir, isMacOs));
    String prefix = getPrefixFromSelector(nameWithSelector, isMacOs);
    if (prefix == null) prefix = defaultPrefix;

    List<Path> candidates = new ArrayList<>();
    for (Path home : homes) {
      if (home == null || !Files.isDirectory(home)) continue;

      try (DirectoryStream<Path> stream = Files.newDirectoryStream(home)) {
        for (Path path : stream) {
          if (!path.equals(isMacOs ? newConfigDir : newConfigDir.getParent()) &&
              startsWithIgnoreCase(path.getFileName().toString(), prefix) &&
              Files.isDirectory(path)) {
            candidates.add(path);
          }
        }
      }
      catch (IOException ignore) { }
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

  private static String getNameWithVersion(Path configDir, boolean isMacOs) {
    return (isMacOs ? configDir : configDir.getParent()).getFileName().toString();
  }

  private static @Nullable String getPrefixFromSelector(String nameWithSelector, boolean isMacOs) {
    Matcher m = Pattern.compile("\\.?([^\\d]+)\\d+(\\.\\d+)?").matcher(nameWithSelector);
    String selector = m.matches() ? m.group(1) : null;
    return StringUtil.isEmpty(selector) ? null : isMacOs ? selector : '.' + selector;
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
      Path configDir = getSettingsPath(selectedDir, PathManager.PROPERTY_CONFIG_PATH, PathManager::getDefaultConfigPathFor);
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
      return;
    }

    Path oldPluginsDir = oldConfigDir.resolve(PLUGINS);
    if (SystemInfo.isMac && !Files.isDirectory(oldPluginsDir)) {
      oldPluginsDir = null;
      if (oldIdeHome != null) {
        oldPluginsDir = getSettingsPath(oldIdeHome, PathManager.PROPERTY_PLUGINS_PATH, PathManager::getDefaultPluginPathFor);
      }
      if (oldPluginsDir == null) {
        oldPluginsDir = oldConfigDir.getFileSystem().getPath(PathManager.getDefaultPluginPathFor(oldConfigDir.getFileName().toString()));
      }
    }

    Path newPluginsDir = newConfigDir.getFileSystem().getPath(PathManager.getPluginsPath());

    try {
      doImport(oldConfigDir, newConfigDir, oldIdeHome, oldPluginsDir, newPluginsDir, log);
    }
    catch (Exception e) {
      log.warn(e);
      String message = ApplicationBundle.message("error.unable.to.import.settings", e.getMessage());
      Main.showMessage(ApplicationBundle.message("title.settings.import.failed"), message, false);
    }
  }

  static void doImport(@NotNull Path oldConfigDir,
                       @NotNull Path newConfigDir,
                       @Nullable Path oldIdeHome,
                       @NotNull Path oldPluginsDir,
                       @NotNull Path newPluginsDir,
                       @NotNull Logger log) throws IOException {
    if (Files.isRegularFile(oldConfigDir)) {
      new Decompressor.Zip(oldConfigDir.toFile()).extract(newConfigDir.toFile());
      return;
    }

    // copy everything except plugins
    // the filter prevents web token reuse and accidental overwrite of files already created by this instance (port/lock/tokens etc.)
    FileUtil.copyDir(oldConfigDir.toFile(), newConfigDir.toFile(), file -> !blockImport(file.toPath(), oldConfigDir, newConfigDir, oldPluginsDir));
    // copy plugins, unless new plugin directory is not empty (the plugin manager will sort out incompatible ones)
    if (isEmptyDirectory(newPluginsDir)) {
      FileUtil.copyDir(oldPluginsDir.toFile(), newPluginsDir.toFile());
    }

    if (SystemInfo.isMac && (PlatformUtils.isIntelliJ() || "AndroidStudio".equals(PlatformUtils.getPlatformPrefix()))) {
      setKeymapIfNeeded(oldConfigDir, newConfigDir, log);
    }

    // apply stale plugin updates
    if (Files.isDirectory(oldPluginsDir)) {
      Path oldSystemDir = null;
      if (oldIdeHome != null) {
        oldSystemDir = getSettingsPath(oldIdeHome, PathManager.PROPERTY_SYSTEM_PATH, PathManager::getDefaultSystemPathFor);
      }
      if (oldSystemDir == null) {
        String selector = SystemInfo.isMac ? oldConfigDir.getFileName().toString() : StringUtil.trimLeading(oldConfigDir.getParent().getFileName().toString(), '.');
        oldSystemDir = oldConfigDir.getFileSystem().getPath(PathManager.getDefaultSystemPathFor(selector));
      }
      Path script = oldSystemDir.resolve(PLUGINS + '/' + StartupActionScriptManager.ACTION_SCRIPT_FILE);  // PathManager#getPluginTempPath
      if (Files.isRegularFile(script)) {
        StartupActionScriptManager.executeActionScript(script.toFile(), oldPluginsDir.toFile(), new File(PathManager.getPluginsPath()));
      }
    }

    updateVMOptions(newConfigDir, log);
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
          else if (line.startsWith("-agentlib:yjpagent")) {
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
}