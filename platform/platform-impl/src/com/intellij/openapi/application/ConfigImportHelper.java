// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application;

import com.intellij.ide.cloudConfig.CloudConfigProvider;
import com.intellij.ide.startup.StartupActionScriptManager;
import com.intellij.idea.Main;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.PropertyResourceBundle;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.openapi.util.Pair.pair;

/**
 * @author max
 */
public class ConfigImportHelper {
  private static final String FIRST_SESSION_KEY = "intellij.first.ide.session";
  private static final String CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY = "intellij.config.imported.in.current.session";

  private static final String CONFIG = "config";
  private static final String OPTIONS_XML = "options/options.xml";
  private static final String BIN = "bin";
  private static final String CONTENTS = "Contents";
  private static final String PLIST = "Info.plist";
  private static final String PLUGINS = "plugins";

  private ConfigImportHelper() { }

  public static void importConfigsTo(@NotNull String newConfigPath) {
    System.setProperty(FIRST_SESSION_KEY, Boolean.TRUE.toString());

    ConfigImportSettings settings = getConfigImportSettings();
    File newConfigDir = new File(newConfigPath);
    File guessedOldConfigDir = findRecentConfigDirectory(newConfigDir);

    ImportOldConfigsPanel dialog = new ImportOldConfigsPanel(guessedOldConfigDir, f -> findConfigDirectoryByPath(f));
    dialog.setModalityType(Dialog.ModalityType.TOOLKIT_MODAL);
    AppUIUtil.updateWindowIcon(dialog);
    dialog.setVisible(true);

    Pair<File, File> result = dialog.getSelectedFile();
    if (result != null) {
      doImport(result.first, newConfigDir, result.second);
      settings.importFinished(newConfigPath);
      System.setProperty(CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY, Boolean.TRUE.toString());
    }

    CloudConfigProvider provider = CloudConfigProvider.getProvider();
    if (provider != null) {
      provider.importFinished(newConfigDir);
    }
  }

  /**
   * Returns {@code true} when the IDE is launched for the first time (i.e. there was no config directory).
   */
  public static boolean isFirstSession() {
    return Boolean.getBoolean(FIRST_SESSION_KEY);
  }

  /**
   * Returns {@code true} when the IDE is launched for the first time, and configs were imported from another installation.
   */
  public static boolean isConfigImported() {
    return Boolean.getBoolean(CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY);
  }

  private static ConfigImportSettings getConfigImportSettings() {
    try {
      String customProviderName = "com.intellij.openapi.application." + PlatformUtils.getPlatformPrefix() + "ConfigImportSettings";
      @SuppressWarnings("unchecked") Class<ConfigImportSettings> customProviderClass = (Class<ConfigImportSettings>)Class.forName(customProviderName);
      if (ConfigImportSettings.class.isAssignableFrom(customProviderClass)) {
        return ReflectionUtil.newInstance(customProviderClass);
      }
    }
    catch (ClassNotFoundException | RuntimeException ignored) { }
    return new ConfigImportSettings();
  }

  @Nullable
  private static File findRecentConfigDirectory(File newConfigDir) {
    // looks for the most recent existing config directory in the vicinity of the new one, assuming standard layout
    // ("~/Library/<selector_prefix><selector_version>" on macOS, "~/.<selector_prefix><selector_version>/config" on other OSes)

    File configsHome = (SystemInfo.isMac ? newConfigDir : newConfigDir.getParentFile()).getParentFile();
    if (configsHome == null || !configsHome.isDirectory()) {
      return null;
    }

    String nameWithSelector = PathManager.getPathsSelector();
    if (nameWithSelector == null) {
      nameWithSelector = (SystemInfo.isMac ? newConfigDir : newConfigDir.getParentFile()).getName();
    }
    String prefix = getPrefixFromSelector(nameWithSelector);
    if (prefix == null) {
      return null;
    }

    File[] candidates = configsHome.listFiles((file, name) -> StringUtil.startsWithIgnoreCase(name, prefix));

    File result = null;
    if (candidates != null && candidates.length > 0) {
      long lastModified = 0;
      for (File child : candidates) {
        File candidate = SystemInfo.isMac ? child : new File(child, CONFIG);
        long modified = new File(candidate, OPTIONS_XML).lastModified();
        if (modified > lastModified) {
          lastModified = modified;
          result = candidate;
        }
      }
    }
    return result;
  }

  @Nullable
  private static String getPrefixFromSelector(String nameWithSelector) {
    Matcher m = Pattern.compile("\\.?([^\\d]+)\\d+(\\.\\d+)?").matcher(nameWithSelector);
    String selector = m.matches() ? m.group(1) : null;
    return StringUtil.isEmpty(selector) ? null : SystemInfo.isMac ? selector : '.' + selector;
  }

  @Nullable
  private static Pair<File, File> findConfigDirectoryByPath(File selectedDir) {
    // tries to map a user selection into a valid config directory
    // returns a pair of a config directory and an IDE home (when a user pointed to it; null otherwise)

    if (isValidConfigDir(selectedDir)) {
      return pair(selectedDir, null);
    }

    File config = new File(selectedDir, CONFIG);
    if (isValidConfigDir(config)) {
      return pair(config, null);
    }

    if (new File(selectedDir, SystemInfo.isMac ? CONTENTS : BIN).isDirectory()) {
      File configDir = getSettingsPath(selectedDir, PathManager.PROPERTY_CONFIG_PATH, PathManager::getDefaultConfigPathFor);
      if (isValidConfigDir(configDir)) {
        return pair(configDir, selectedDir);
      }
    }

    return null;
  }

  private static boolean isValidConfigDir(File candidate) {
    return new File(candidate, OPTIONS_XML).isFile();
  }

  @Nullable
  private static File getSettingsPath(File ideHome, String propertyName, Function<String, String> pathBySelector) {
    List<File> files = new ArrayList<>();
    if (SystemInfo.isMac) {
      files.add(new File(ideHome, CONTENTS + '/' + BIN + '/' + PathManager.PROPERTIES_FILE_NAME));
      files.add(new File(ideHome, CONTENTS + '/' + PLIST));
    }
    else {
      files.add(new File(ideHome, BIN + '/' + PathManager.PROPERTIES_FILE_NAME));
      String scriptName = ApplicationNamesInfo.getInstance().getScriptName();
      files.add(new File(ideHome, BIN + '/' + scriptName + ".bat"));
      files.add(new File(ideHome, BIN + '/' + scriptName + ".sh"));
    }

    // explicitly specified directory
    for (File file : files) {
      if (file.isFile()) {
        String candidatePath = PathManager.substituteVars(getPropertyFromFile(file, propertyName), ideHome.getPath());
        if (candidatePath != null) {
          File candidate = new File(candidatePath);
          if (candidate.isDirectory()) {
            return candidate.getAbsoluteFile();
          }
        }
      }
    }

    // default directory
    for (File file : files) {
      if (file.isFile()) {
        String selector = getPropertyFromFile(file, PathManager.PROPERTY_PATHS_SELECTOR);
        if (selector != null) {
          File candidate = new File(pathBySelector.apply(selector));
          if (candidate.isDirectory()) {
            return candidate;
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private static String getPropertyFromFile(File file, String propertyName) {
    try {
      String fileContent = FileUtil.loadFile(file);

      if (file.getName().endsWith(".properties")) {
        PropertyResourceBundle bundle = new PropertyResourceBundle(new StringReader(fileContent));
        return bundle.containsKey(propertyName) ? bundle.getString(propertyName) : null;
      }

      if (file.getName().endsWith(".plist")) {
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

  private static void doImport(File oldConfigDir, File newConfigDir, @Nullable File oldIdeHome) {
    if (FileUtil.filesEqual(oldConfigDir, newConfigDir)) {
      return;
    }

    try {
      // copy everything including plugins (the plugin manager will sort out incompatible ones)
      FileUtil.copyDir(oldConfigDir, newConfigDir);

      // tokens must not be reused
      FileUtil.delete(new File(newConfigDir, "user.token"));
      FileUtil.delete(new File(newConfigDir, "user.web.token"));

      // on macOS, plugins are normally not under the config directory
      File oldPluginsDir = new File(oldConfigDir, PLUGINS);
      if (SystemInfo.isMac && !oldPluginsDir.isDirectory()) {
        oldPluginsDir = null;
        if (oldIdeHome != null) {
          oldPluginsDir = getSettingsPath(oldIdeHome, PathManager.PROPERTY_PLUGINS_PATH, PathManager::getDefaultPluginPathFor);
        }
        if (oldPluginsDir == null) {
          oldPluginsDir = new File(PathManager.getDefaultPluginPathFor(oldConfigDir.getName()));
        }
        if (oldPluginsDir.isDirectory()) {
          File newPluginsDir = new File(PathManager.getPluginsPath());
          FileUtil.copyDir(oldPluginsDir, newPluginsDir);
        }
      }

      // apply stale plugin updates
      if (oldPluginsDir.isDirectory()) {
        File oldSystemDir = null;
        if (oldIdeHome != null) {
          oldSystemDir = getSettingsPath(oldIdeHome, PathManager.PROPERTY_SYSTEM_PATH, PathManager::getDefaultSystemPathFor);
        }
        if (oldSystemDir == null) {
          String selector = SystemInfo.isMac ? oldConfigDir.getName() : StringUtil.trimLeading(oldConfigDir.getParentFile().getName(), '.');
          oldSystemDir = new File(PathManager.getDefaultSystemPathFor(selector));
        }
        File script = new File(oldSystemDir, PLUGINS + '/' + StartupActionScriptManager.ACTION_SCRIPT_FILE);  // PathManager#getPluginTempPath
        if (script.isFile()) {
          File newPluginsDir = new File(PathManager.getPluginsPath());
          StartupActionScriptManager.executeActionScript(script, oldPluginsDir, newPluginsDir);
        }
      }
    }
    catch (IOException e) {
      String message = ApplicationBundle.message("error.unable.to.import.settings", e.getMessage());
      Main.showMessage(ApplicationBundle.message("title.settings.import.failed"), message, false);
    }
  }
}