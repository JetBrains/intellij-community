/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.SystemProperties;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.PropertyResourceBundle;

/**
 * @author max
 */
public class ConfigImportHelper {
  /**
   * Holds name of the system property that is supposed to hold <code>'true'</code> value when IDE settings have been
   * imported on the current startup
   */
  @NonNls public static final String CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY = "intellij.config.imported.in.current.session";
  
  @NonNls private static final String BUILD_NUMBER_FILE = "build.txt";
  @NonNls private static final String PLUGINS_PATH = "plugins";
  @NonNls private static final String BIN_FOLDER = "bin";
  @NonNls private static final String CONFIG_RELATED_PATH = SystemInfo.isMac ? "" : "config/";
  @NonNls private static final String OPTIONS_XML = "options/options.xml";

  private ConfigImportHelper() {
  }

  public static void importConfigsTo(String newConfigPath) {
    ConfigImportSettings settings = getConfigImportSettings();

    File oldConfigDir = findOldConfigDir(newConfigPath, settings.getCustomPathsSelector());
    do {
      ImportOldConfigsPanel dialog = new ImportOldConfigsPanel(oldConfigDir, settings);
      dialog.setModalityType(Dialog.ModalityType.TOOLKIT_MODAL);
      AppUIUtil.updateWindowIcon(dialog);
      dialog.setVisible(true);
      if (dialog.isImportEnabled()) {
        File instHome = dialog.getSelectedFile();
        oldConfigDir = getOldConfigDir(instHome, settings);
        if (!validateOldConfigDir(instHome, oldConfigDir, settings)) continue;

        doImport(newConfigPath, oldConfigDir);
        settings.importFinished(newConfigPath);
        System.setProperty(CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY, Boolean.TRUE.toString());
      }

      break;
    }
    while (true);
  }

  private static ConfigImportSettings getConfigImportSettings() {
    try {
      Class customProviderClass =
        Class.forName("com.intellij.openapi.application." + PlatformUtils.getPlatformPrefix() + "ConfigImportSettings");
      if (customProviderClass != null) {
        if (ConfigImportSettings.class.isAssignableFrom(customProviderClass)) {
          Constructor constructor = customProviderClass.getDeclaredConstructor();
          if (constructor != null) {
            return (ConfigImportSettings)constructor.newInstance();
          }
        }
      }
    }
    catch (ClassNotFoundException ignored) {
    }
    catch (NoSuchMethodException ignored) {
    }
    catch (InvocationTargetException ignored) {
    }
    catch (InstantiationException ignored) {
    }
    catch (IllegalAccessException ignored) {
    }
    return new ConfigImportSettings();
  }

  @Nullable
  private static File findOldConfigDir(String newConfigPath, @Nullable String customPathSelector) {
    final File configDir = new File(newConfigPath);
    final File selectorDir = CONFIG_RELATED_PATH.length() == 0 ? configDir : configDir.getParentFile();
    final File parent = selectorDir.getParentFile();
    if (parent == null || !parent.exists()) return null;
    File maxFile = null;
    long lastModified = 0;
    final String selector;
    if (customPathSelector != null) {
      selector = customPathSelector;
    }
    else {
      selector = PathManager.getPathsSelector() != null ? PathManager.getPathsSelector() : selectorDir.getName();
    }

    final String prefix = (SystemInfo.isMac ? "" : ".") + selector.replaceAll("\\d", "");
    for (File file : parent.listFiles(new FilenameFilter() {
      @Override
      public boolean accept(File file, String name) {
        return StringUtil.startsWithIgnoreCase(name, prefix);
      }
    })) {
      final File options = new File(file, CONFIG_RELATED_PATH + OPTIONS_XML);
      if (!options.exists()) continue;
      final long modified = options.lastModified();
      if (modified > lastModified) {
        lastModified = modified;
        maxFile = file;
      }
    }
    return maxFile != null ? new File(maxFile, CONFIG_RELATED_PATH) : null;
  }

  public static void doImport(final String newConfigPath, final File oldConfigDir) {
    try {
      xcopy(oldConfigDir, new File(newConfigPath));
    }
    catch (IOException e) {
      JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                    ApplicationBundle.message("error.unable.to.import.settings", e.getMessage()),
                                    ApplicationBundle.message("title.settings.import.failed"), JOptionPane.WARNING_MESSAGE);
    }
  }

  public static boolean validateOldConfigDir(final File instHome, final File oldConfigDir, ConfigImportSettings settings) {
    if (oldConfigDir == null) {
      final String message = !instHome.equals(oldConfigDir) ?
                             ApplicationBundle.message("error.invalid.installation.home", instHome.getAbsolutePath(),
                                                       settings.getProductName(ThreeState.YES)) :
                             ApplicationBundle.message("error.invalid.config.folder", instHome.getAbsolutePath(),
                                                       settings.getProductName(ThreeState.YES));
      JOptionPane.showMessageDialog(JOptionPane.getRootFrame(), message);
      return false;
    }

    if (!oldConfigDir.exists()) {
      JOptionPane.showMessageDialog(JOptionPane.getRootFrame(),
                                    ApplicationBundle.message("error.no.settings.path",
                                                              oldConfigDir.getAbsolutePath()),
                                    ApplicationBundle.message("title.settings.import.failed"), JOptionPane.WARNING_MESSAGE);
      return false;
    }
    return true;
  }

  public static void xcopy(File src, File dest) throws IOException {
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

    FileUtil.copyDir(src, dest);

    // Delete plugins just imported. They're most probably incompatible with newer idea version.
    File plugins = new File(dest, PLUGINS_PATH);
    if (plugins.exists()) {
      final ArrayList<IdeaPluginDescriptorImpl> descriptors = new ArrayList<IdeaPluginDescriptorImpl>();
      PluginManager.loadDescriptors(plugins.getPath(), descriptors, null, 0);
      final ArrayList<String> oldPlugins = new ArrayList<String>();
      for (IdeaPluginDescriptorImpl descriptor : descriptors) {
        oldPlugins.add(descriptor.getPluginId().getIdString());
      }
      if (!oldPlugins.isEmpty()) {
        PluginManager.savePluginsList(oldPlugins, false, new File(dest, PluginManager.INSTALLED_TXT));
      }
      FileUtil.delete(plugins);
    }
    
    File pluginsSettings = new File(new File(dest, "options"), "plugin_ui.xml");
    if (pluginsSettings.exists()) {
      FileUtil.delete(pluginsSettings);
    }
  }

  @Nullable
  public static File getOldConfigDir(File oldInstallHome, ConfigImportSettings settings) {
    if (oldInstallHome == null) return null;
    // check if it's already config dir
    if (new File(oldInstallHome, OPTIONS_XML).exists()) {
      return oldInstallHome;
    }
    if (new File(oldInstallHome, CONFIG_RELATED_PATH + OPTIONS_XML).exists()) {
      return new File(oldInstallHome, CONFIG_RELATED_PATH);
    }

    int oldBuildNumber = getBuildNumber(oldInstallHome);

    if (oldBuildNumber != -1 && oldBuildNumber <= 600) { // Pandora
      //noinspection HardCodedStringLiteral
      return new File(oldInstallHome, "config");
    }

    final File[] launchFileCandidates = getLaunchFilesCandidates(oldInstallHome, settings);

    // custom config folder
    for (File candidate : launchFileCandidates) {
      if (candidate.exists()) {
        String configDir = PathManager.substituteVars(getPropertyFromLaxFile(candidate, PathManager.PROPERTY_CONFIG_PATH),
                                                      oldInstallHome.getPath());
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
          final String configDir = PathManager.getDefaultConfigPathFor(pathsSelector);
          final File probableConfig = new File(configDir);
          if (probableConfig.exists()) {
            return probableConfig;
          }
        }
      }
    }

    return null;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static File[] getLaunchFilesCandidates(@NotNull final File instHome, @NotNull final ConfigImportSettings settings) {
    final File bin = new File(instHome, BIN_FOLDER);
    final List<File> files = new ArrayList<File>();
    if (SystemInfo.isMac) {
      // Info.plist
      files.add(new File(new File(instHome, "Contents"), "Info.plist"));

      files.add(new File(new File(new File(bin, "idea.app"), "Contents"), "Info.plist"));
      files.add(new File(new File(new File(instHome, "idea.app"), "Contents"), "Info.plist"));
    }
    // idea.properties
    files.add(new File(bin, "idea.properties"));

    
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
    return files.toArray(new File[files.size()]);
  }

  private static void addLaunchExecutableScriptsCandidates(final List<File> files,
                                                           final String executableName,
                                                           final File binFolder) {
    files.add(new File(binFolder, executableName + ".lax"));
    files.add(new File(binFolder, executableName + ".bat"));
    files.add(new File(binFolder, executableName + ".sh"));
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @Nullable
  public static String getPropertyFromLaxFile(@NotNull final File file,
                                              @NotNull final String propertyName) {
    if (file.getName().endsWith(".properties")) {
      try {
        InputStream fis = new BufferedInputStream(new FileInputStream(file));
        PropertyResourceBundle bundle;
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
  private static String findProperty(final String propertyName, 
                                     final String fileContent) {
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
      StringBuffer content = new StringBuffer();
      BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
      try {
        do {
          String line = reader.readLine();
          if (line == null) break;
          content.append(line);
          content.append('\n');
        }
        while (true);
      }
      finally {
        reader.close();
      }

      return content.toString();
    }
    catch (Exception e) {
      return null;
    }
  }

  public static String fixDirName(String dir, boolean replaceUserHome) {
    if (StringUtil.startsWithChar(dir, '\"') && StringUtil.endsWithChar(dir, '\"')) {
      dir = dir.substring(1, dir.length() - 1);
    }
    if (replaceUserHome) {
      if (dir.startsWith("~\\") || dir.startsWith("~//") || StringUtil.startsWithConcatenation(dir, "~", File.separator)) {
        dir = SystemProperties.getUserHome() + dir.substring(1);
      }
    }
    return dir;
  }

  public static boolean isInstallationHomeOrConfig(@NotNull final String installationHome, 
                                                   @NotNull final ConfigImportSettings settings) {
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
      //noinspection HardCodedStringLiteral
      if (new File(libFolder, mainJarName).exists()) {
        quickTest = true;
        break;
      }
    }
    if (!quickTest) return false;

    File[] files = getLaunchFilesCandidates(new File(installationHome), settings);
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
