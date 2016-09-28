/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.idea.Main;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.PropertyResourceBundle;

/**
 * @author max
 */
public class ConfigImportHelper {
  /** This should only be done in EAP builds! */
  private static final boolean SKIP_UPDATE_CHANNEL_IMPORT = true;

  private static final String FIRST_SESSION_KEY = "intellij.first.ide.session";
  private static final String CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY = "intellij.config.imported.in.current.session";

  private static final String BUILD_NUMBER_FILE = SystemInfo.isMac ? "/Resources/build.txt" : "build.txt";
  private static final String PLUGINS_PATH = "plugins";
  private static final String BIN_FOLDER = "bin";
  private static final String CONFIG_RELATED_PATH = SystemInfo.isMac ? "" : "config/";
  private static final String OPTIONS_XML = "options/options.xml";

  private ConfigImportHelper() { }

  public static void importConfigsTo(@NotNull String newConfigPath) {
    System.setProperty(FIRST_SESSION_KEY, Boolean.TRUE.toString());

    ConfigImportSettings settings = getConfigImportSettings();

    File newConfigDir = new File(newConfigPath);
    File oldConfigDir = findOldConfigDir(newConfigDir, settings.getCustomPathsSelector());

    try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
    catch (Throwable ignore) { }

    while (true) {
      ImportOldConfigsPanel dialog = new ImportOldConfigsPanel(oldConfigDir, settings);
      dialog.setModalityType(Dialog.ModalityType.TOOLKIT_MODAL);
      AppUIUtil.updateWindowIcon(dialog);
      dialog.setVisible(true);
      if (!dialog.isImportEnabled()) {
        break;
      }

      File installationHome = dialog.getSelectedFile();
      oldConfigDir = getOldConfigDir(installationHome, settings);
      if (!validateOldConfigDir(installationHome, oldConfigDir, settings)) {
        continue;
      }

      assert oldConfigDir != null;
      doImport(newConfigDir, oldConfigDir, settings, installationHome);
      settings.importFinished(newConfigPath);
      System.setProperty(CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY, Boolean.TRUE.toString());
      break;
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

  @NotNull
  private static ConfigImportSettings getConfigImportSettings() {
    try {
      @SuppressWarnings("unchecked")
      Class<ConfigImportSettings> customProviderClass =
        (Class<ConfigImportSettings>)Class.forName("com.intellij.openapi.application." + PlatformUtils.getPlatformPrefix() + "ConfigImportSettings");
      if (ConfigImportSettings.class.isAssignableFrom(customProviderClass)) {
        return ReflectionUtil.newInstance(customProviderClass);
      }
    }
    catch (ClassNotFoundException ignored) { }
    catch (RuntimeException ignored) { }
    return new ConfigImportSettings();
  }

  @Nullable
  private static File findOldConfigDir(@NotNull File configDir, @Nullable String customPathSelector) {
    final File selectorDir = CONFIG_RELATED_PATH.isEmpty() ? configDir : configDir.getParentFile();
    final File parent = selectorDir.getParentFile();
    if (parent == null || !parent.exists()) {
      return null;
    }
    File maxFile = null;
    long lastModified = 0;
    final String selector = PathManager.getPathsSelector() != null ? PathManager.getPathsSelector() : selectorDir.getName();

    final String prefix = getPrefixFromSelector(selector);
    final String customPrefix = customPathSelector != null ? getPrefixFromSelector(customPathSelector) : null;
    for (File file : parent.listFiles((file1, name) -> StringUtil.startsWithIgnoreCase(name, prefix) ||
           customPrefix != null && StringUtil.startsWithIgnoreCase(name, customPrefix))) {
      File options = new File(file, CONFIG_RELATED_PATH + OPTIONS_XML);
      if (!options.exists()) {
        continue;
      }

      long modified = options.lastModified();
      if (modified > lastModified) {
        lastModified = modified;
        maxFile = file;
      }
    }

    // Android Studio: Attempt to find user settings from earlier versions where the settings names
    // are different from the current setting name. We want to import from the user's most recently actively
    // used IDE install. If the user has used both Android Studio 1.1 and Android Studio 1.2, we want to
    // import Android Studio 1.2 settings.
    //
    // The reason we even look for old (Android Studio 1.0) settings here is such that users who skipped a
    // release will still be able to import any settings they've configured in the past.
    //
    // Looking at previous versions is complicated slightly by preview versions.
    // Consider the update to 1.3 final. Should that import from 1.2 final, or 1.3 preview?
    // That depends; a user may have tried 1.3 preview (and imported 1.2 settings into it), but then
    // the user gave up on 1.3 preview due to a blocking bug, and continued working and configuring
    // 1.2. When 1.3 final comes out, should we import 1.3 preview, or the more recently updated 1.2
    // settings?
    //
    // For now, we try the latter: We look at the time stamps of the settings folders
    // and attempt to pick the most recently modified one. Note that this may not work perfectly: on
    // some operating systems, updating contents in a folder does *not* update the last modified timestamp
    // of the folder itself. Therefore, when file stamps are equal (as can be the case when a config
    // file is copied from an older version to the next), we take the later version. That way, we'll
    // prefer the highest version number, unless the older version has a more recent modification
    // date.
    if (maxFile == null) {
      File v1_1 = new File(PathManager.getDefaultConfigPathFor("AndroidStudio"));
      File v1_2 = new File(PathManager.getDefaultConfigPathFor("AndroidStudio1.2"));
      File v1_3 = new File(PathManager.getDefaultConfigPathFor("AndroidStudio1.3"));
      File v1_4 = new File(PathManager.getDefaultConfigPathFor("AndroidStudio1.4"));
      File v1_5 = new File(PathManager.getDefaultConfigPathFor("AndroidStudio1.5"));
      File v2_0 = new File(PathManager.getDefaultConfigPathFor("AndroidStudio2.0"));
      File v21preview = new File(PathManager.getDefaultConfigPathFor("AndroidStudioPreview2.1"));
      File v2_1 = new File(PathManager.getDefaultConfigPathFor("AndroidStudio2.1"));
      File preview = new File(PathManager.getDefaultConfigPathFor("AndroidStudioPreview2.2"));
      File v2_2 = new File(PathManager.getDefaultConfigPathFor("AndroidStudio2.2"));
      for (File file : new File[] { v1_1, v1_2, v1_3, v1_4, v1_5, v2_0, v21preview, v2_1, preview, v2_2 }) {
        if (!file.isDirectory()) {
          continue;
        }
        File options = new File(file, OPTIONS_XML);
        if (options.exists()) {
          final long modified = file.lastModified();
          if (modified >= lastModified) {
            lastModified = modified;
            maxFile = file;
          }
        }
      }
      if (maxFile != null) {
        // Already have the config folder so don't prepend CONFIG_RELATED_PATH below
        return maxFile;
      }
    }

    return maxFile != null ? new File(maxFile, CONFIG_RELATED_PATH) : null;
  }

  private static String getPrefixFromSelector(String selector) {
    return (SystemInfo.isMac ? "" : ".") + selector.replaceAll("\\d(\\.\\d)?", "");
  }

  private static void doImport(@NotNull File newConfigDir, @NotNull File oldConfigDir, ConfigImportSettings settings, File installationHome) {
    try {
      copy(oldConfigDir, newConfigDir, settings, installationHome);

      // There are a couple of files that live outside the system/ and config/ folders; handle
      // these here; see https://code.google.com/p/android/issues/detail?id=171122
      if (!SystemInfo.isMac) {
        File oldParent = oldConfigDir.getParentFile();
        File newParent = newConfigDir.getParentFile();
        if (oldParent != null && newParent!= null) {
          File[] files = oldParent.listFiles();
          if (files != null) {
            for (File file : files) {
              String path = file.getPath(); // When used with .endsWith(), faster than (and same result as) getName()
              if (path.endsWith(".properties") || path.endsWith(".vmoptions")) {
                File dest = new File(newParent, file.getName());
                Files.copy(file, dest);
              }
            }
          }
        }
      }
    }
    catch (IOException e) {
      String message = ApplicationBundle.message("error.unable.to.import.settings", e.getMessage());
      Main.showMessage(ApplicationBundle.message("title.settings.import.failed"), message, false);
    }
  }

  private static boolean validateOldConfigDir(@Nullable File installationHome, @Nullable File oldConfigDir, @NotNull ConfigImportSettings settings) {
    if (oldConfigDir == null) {
      if (installationHome != null) {
        String message = ApplicationBundle.message("error.invalid.installation.home", installationHome.getAbsolutePath(), settings.getProductName(ThreeState.YES));
        Main.showMessage(ApplicationBundle.message("title.settings.import.failed"), message, false);
      }
      return false;
    }

    if (!oldConfigDir.exists()) {
      String message = ApplicationBundle.message("error.no.settings.path", oldConfigDir.getAbsolutePath());
      Main.showMessage(ApplicationBundle.message("title.settings.import.failed"), message, false);
      return false;
    }

    return true;
  }

  private static void copy(@NotNull File src, @NotNull File dest, ConfigImportSettings settings, File oldInstallationHome) throws IOException {
    src = src.getCanonicalFile();
    dest = dest.getCanonicalFile();
    if (!src.isDirectory()) {
      throw new IOException(ApplicationBundle.message("config.import.invalid.directory.error", src.getAbsolutePath()));
    }
    if (!dest.isDirectory()) {
      throw new IOException(ApplicationBundle.message("config.import.invalid.directory.error", dest.getAbsolutePath()));
    }
    if (FileUtil.filesEqual(src, dest)) {
      return;
    }

    FileUtil.ensureExists(dest);

    // Copy old plugins. If some of them are incompatible PluginManager will deal with it
    FileUtil.copyDir(src, dest);

    // delete old user token - we must not reuse it
    FileUtil.delete(new File(dest, "user.token"));
    FileUtil.delete(new File(dest, "user.web.token"));

    File oldPluginsDir = new File(src, PLUGINS_PATH);
    if (!oldPluginsDir.isDirectory() && SystemInfo.isMac) {
      oldPluginsDir = getSettingsPath(oldInstallationHome, settings, PathManager.PROPERTY_PLUGINS_PATH,
                                      pathSelector -> PathManager.getDefaultPluginPathFor(pathSelector));
      if (oldPluginsDir == null) {
        //e.g. installation home referred to config home. Try with default selector, same as config name
        oldPluginsDir = new File(PathManager.getDefaultPluginPathFor(src.getName()));
      }

      File newPluginsDir = new File(PathManager.getPluginsPath());
      FileUtil.copyDir(oldPluginsDir, newPluginsDir);
    }
    loadOldPlugins(oldPluginsDir, dest);

    // If we're in an EAP build, don't import a stable update channel preference;
    // if you're using this canary build we'll want to offer updates to it.
    // (Ideally we'd only do this if ApplicationManager.getApplication().isEAP(),
    // but settings import happens before the IDE is running so we cannot look up
    // any application context.
    if (SKIP_UPDATE_CHANNEL_IMPORT) {
      File updates = new File(dest, "options" + File.separator + "updates.xml");
      if (updates.exists()) {
        String xml = Files.toString(updates, Charsets.UTF_8);
        int index = xml.indexOf("<option name=\"UPDATE_CHANNEL_TYPE\"");
        if (index != -1) {
          int end = xml.indexOf('>', index);
          if (end != -1) {
            xml = xml.substring(0, index) + xml.substring(end + 1);
            Files.write(xml, updates, Charsets.UTF_8);
          }
        }
      }
    }
  }

  private static boolean loadOldPlugins(File plugins, File dest) throws IOException {
    if (plugins.exists()) {
      List<IdeaPluginDescriptorImpl> descriptors = new SmartList<IdeaPluginDescriptorImpl>();
      PluginManagerCore.loadDescriptors(plugins, descriptors, null, 0);
      List<String> oldPlugins = new SmartList<String>();
      for (IdeaPluginDescriptorImpl descriptor : descriptors) {
        // check isBundled also - probably plugin is bundled in new IDE version
        if (descriptor.isEnabled() && !descriptor.isBundled()) {
          oldPlugins.add(descriptor.getPluginId().getIdString());
        }
      }
      if (!oldPlugins.isEmpty()) {
        PluginManagerCore.savePluginsList(oldPlugins, false, new File(dest, PluginManager.INSTALLED_TXT));
      }
      return true;
    }
    return false;
  }

  @Nullable
  private static File getOldConfigDir(@Nullable File oldInstallHome, ConfigImportSettings settings) {
    if (oldInstallHome == null) {
      return null;
    }

    // check if it's already config dir
    if (new File(oldInstallHome, OPTIONS_XML).exists()) {
      return oldInstallHome;
    }
    if (new File(oldInstallHome, CONFIG_RELATED_PATH + OPTIONS_XML).exists()) {
      return new File(oldInstallHome, CONFIG_RELATED_PATH);
    }

    int oldBuildNumber = getBuildNumber(oldInstallHome);

    if (oldBuildNumber != -1 && oldBuildNumber <= 600) { // Pandora
      return new File(oldInstallHome, "config");
    }

    return getSettingsPath(oldInstallHome, settings, PathManager.PROPERTY_CONFIG_PATH,
                           pathsSelector -> PathManager.getDefaultConfigPathFor(pathsSelector));
  }

  private static File getSettingsPath(File installHome, ConfigImportSettings settings, String propertyName, Function<String, String> fromPathSelector) {
    final List<File> launchFileCandidates = getLaunchFilesCandidates(installHome, settings);

    // custom config folder
    for (File candidate : launchFileCandidates) {
      if (candidate.exists()) {
        String configDir = PathManager.substituteVars(getPropertyFromLaxFile(candidate, propertyName),
                                                      installHome.getPath());
        if (configDir != null) {
          File probableConfig = new File(configDir);
          if (probableConfig.exists()) return probableConfig;
        }
      }
    }

    // custom config folder not found - use paths selector
    for (File candidate : launchFileCandidates) {
      if (candidate.exists()) {
        final String pathsSelector = getPropertyFromLaxFile(candidate, PathManager.PROPERTY_PATHS_SELECTOR);
        if (pathsSelector != null) {
          File candidateDir = new File(fromPathSelector.fun(pathsSelector));
          if (candidateDir.exists()) {
            return candidateDir;
          }
        }
      }
    }

    return null;
  }

  private static List<File> getLaunchFilesCandidates(@NotNull File instHome, @NotNull ConfigImportSettings settings) {
    final File bin = new File(instHome, BIN_FOLDER);
    final List<File> files = new ArrayList<File>();
    if (SystemInfo.isMac) {
      // Info.plist
      files.add(new File(new File(instHome, "Contents"), "Info.plist"));
      files.add(new File(new File(new File(bin, "idea.app"), "Contents"), "Info.plist"));
      files.add(new File(new File(new File(instHome, "idea.app"), "Contents"), "Info.plist"));
    }
    // idea.properties
    files.add(new File(bin, PathManager.PROPERTIES_FILE_NAME));

    // other binary scripts
    final String executableName = StringUtil.toLowerCase(settings.getExecutableName());
    // * defaults:
    addLaunchExecutableScriptsCandidates(files, executableName, bin);
    // * customized files:
    files.addAll(settings.getCustomLaunchFilesCandidates(instHome, bin));
    // * legacy support:
    if (!"idea".equals(executableName)) {
      // for compatibility with some platform-base IDEs with wrong executable names
      addLaunchExecutableScriptsCandidates(files, "idea", bin);
    }
    return files;
  }

  private static void addLaunchExecutableScriptsCandidates(List<File> files, String executableName, File binFolder) {
    files.add(new File(binFolder, executableName + ".lax"));
    files.add(new File(binFolder, executableName + ".bat"));
    files.add(new File(binFolder, executableName + ".sh"));
  }

  @Nullable
  private static String getPropertyFromLaxFile(@NotNull File file, @NotNull String propertyName) {
    if (file.getName().endsWith(".properties")) {
      try {
        PropertyResourceBundle bundle;
        InputStream fis = new BufferedInputStream(new FileInputStream(file));
        try {
          bundle = new PropertyResourceBundle(fis);
        }
        finally {
          fis.close();
        }
        if (bundle.containsKey(propertyName)) {
          return bundle.getString(propertyName);
        }
        return null;
      }
      catch (IOException e) {
        return null;
      }
    }

    final String fileContent = getContent(file);

    // try to find custom config path
    final String propertyValue = findProperty(propertyName, fileContent);
    if (!StringUtil.isEmpty(propertyValue)) {
      return propertyValue;
    }

    return null;
  }

  @Nullable
  private static String findProperty(String propertyName, String fileContent) {
    String param = propertyName + "=";
    int idx = fileContent.indexOf(param);
    if (idx == -1) {
      param = "<key>" + propertyName + "</key>";
      idx = fileContent.indexOf(param);
      if (idx == -1) return null;
      idx = fileContent.indexOf("<string>", idx);
      if (idx == -1) return null;
      idx += "<string>".length();
      return fixDirName(fileContent.substring(idx, fileContent.indexOf("</string>", idx)), true);
    }
    else {
      String configDir = "";
      idx += param.length();
      if (fileContent.length() > idx) {
        if (fileContent.charAt(idx) == '"') {
          idx++;
          while ((fileContent.length() > idx) && (fileContent.charAt(idx) != '"') && (fileContent.charAt(idx) != '\n') &&
                 (fileContent.charAt(idx) != '\r')) {
            configDir += fileContent.charAt(idx);
            idx++;
          }
        }
        else {
          while ((fileContent.length() > idx) && (!Character.isSpaceChar(fileContent.charAt(idx))) &&
                 (fileContent.charAt(idx) != '\n') &&
                 (fileContent.charAt(idx) != '\r')) {
            configDir += fileContent.charAt(idx);
            idx++;
          }
        }
      }
      configDir = fixDirName(configDir, true);
      if (configDir.length() > 0) {
        configDir = (new File(configDir)).getPath();
      }
      return configDir;
    }
  }

  @Nullable
  private static String getContent(File file) {
    try {
      return FileUtil.loadFile(file);
    }
    catch (IOException e) {
      return null;
    }
  }

  private static String fixDirName(String dir, boolean replaceUserHome) {
    if (StringUtil.startsWithChar(dir, '\"') && StringUtil.endsWithChar(dir, '\"')) {
      dir = dir.substring(1, dir.length() - 1);
    }
    if (replaceUserHome) {
      dir = FileUtil.expandUserHome(dir);
    }
    return dir;
  }

  public static boolean isInstallationHomeOrConfig(@NotNull String installationHome, @NotNull ConfigImportSettings settings) {
    if (new File(installationHome, OPTIONS_XML).exists()) return true;
    if (new File(installationHome, CONFIG_RELATED_PATH + OPTIONS_XML).exists()) return true;

    if (!new File(installationHome, BIN_FOLDER).exists()) {
      return false;
    }

    File libFolder = new File(installationHome, "lib");
    boolean quickTest = false;
    String[] mainJarNames = settings.getMainJarNames();
    for (String name : mainJarNames) {
      String mainJarName = StringUtil.toLowerCase(name) + ".jar";
      if (new File(libFolder, mainJarName).exists()) {
        quickTest = true;
        break;
      }
    }
    if (!quickTest) return false;

    List<File> files = getLaunchFilesCandidates(new File(installationHome), settings);
    for (File file : files) {
      if (file.exists()) return true;
    }

    return false;
  }

  private static int getBuildNumber(File installDirectory) {
    installDirectory = installDirectory.getAbsoluteFile();

    File buildTxt = new File(installDirectory, BUILD_NUMBER_FILE);
    if ((!buildTxt.exists()) || (buildTxt.isDirectory())) {
      buildTxt = new File(new File(installDirectory, BIN_FOLDER), BUILD_NUMBER_FILE);
    }

    if (buildTxt.exists() && !buildTxt.isDirectory()) {
      int buildNumber = -1;
      String buildNumberText = getContent(buildTxt);
      if (buildNumberText != null) {
        try {
          if (buildNumberText.length() > 1) {
            buildNumberText = buildNumberText.trim();
            buildNumber = Integer.parseInt(buildNumberText);
          }
        }
        catch (Exception e) {
          // OK
        }
      }
      return buildNumber;
    }

    return -1;
  }
}
