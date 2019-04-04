// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.eclipse.importer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.SchemeImportException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

public class EclipseProjectCodeStyleData extends EclipseCodeStylePropertiesImporter {

  private static final Logger LOG = Logger.getInstance(EclipseProjectCodeStyleData.class);

  public final static String CORE_PREFS_FILE_NAME = "org.eclipse.jdt.core.prefs";
  public final static String UI_PREFS_FILE_NAME = "org.eclipse.jdt.ui.prefs";
  public final static String ECLIPSE_SETTINGS_SUBDIR = ".settings";

  private boolean myImportOrganizeImportsConfig;

  private final static Map<String, String> PREDEFINED_ECLIPSE_PROFILES = ContainerUtil.newHashMap();
  static {
    PREDEFINED_ECLIPSE_PROFILES.put("org.eclipse.jdt.ui.default.eclipse_profile", "Eclipse [built-in]");
    PREDEFINED_ECLIPSE_PROFILES.put("org.eclipse.jdt.ui.default.sun_profile", "Java Conventions [built-in]");
    PREDEFINED_ECLIPSE_PROFILES.put("org.eclipse.jdt.ui.default_profile", "Eclipse 2.1 [built-in]");
  }

  private final @NotNull String myProjectPath;
  private @Nullable Properties myCorePreferences;
  private @Nullable Properties myUiPreferences;
  private final String myProjectName;

  public EclipseProjectCodeStyleData(@NotNull String projectName, @NotNull String projectPath) {
    myProjectName = projectName;
    myProjectPath = projectPath;
  }

  public boolean loadEclipsePreferences() {
    try {
      myCorePreferences = loadProperties(CORE_PREFS_FILE_NAME);
      myUiPreferences = loadProperties(UI_PREFS_FILE_NAME);
      myImportOrganizeImportsConfig = isEclipseImportsConfigAvailable();
      return myCorePreferences != null && formatterOptionsExist(myCorePreferences) && myUiPreferences != null;
    }
    catch (IOException e) {
      LOG.warn(e);
    }
    return false;
  }

  private Properties loadProperties(@NotNull String fileName) throws IOException {
    File prefsFile = getPreferencesFile(fileName);
    if (prefsFile != null) {
      Properties properties = new Properties();
      try (InputStream input = new FileInputStream(prefsFile)) {
        properties.load(input);
      }
      return properties;
    }
    return null;
  }

  private static boolean formatterOptionsExist(@NotNull Properties eclipseProperties) {
    for (String propertyKey : eclipseProperties.stringPropertyNames()) {
      if (propertyKey.startsWith(FORMATTER_OPTIONS_PREFIX)) return true;
    }
    return false;
  }

  @Nullable
  private File getPreferencesFile(@NotNull String fileName) {
    String filePath = myProjectPath + File.separator + ECLIPSE_SETTINGS_SUBDIR + File.separator + fileName;
    File prefsFile = new File(filePath);
    return prefsFile.exists() ? prefsFile : null;
  }

  public String getProjectName() {
    return myProjectName;
  }

  @Override
  public String toString() {
    String profileName = getFormatterProfileName();
    return myProjectName + (profileName != null ? ": " + profileName : "");
  }

  @Nullable
  private String getFormatterProfileName() {
    String rawName = myUiPreferences != null ? myUiPreferences.getProperty(OPTION_FORMATTER_PROFILE) : null;
    if (rawName != null) {
      if (PREDEFINED_ECLIPSE_PROFILES.containsKey(rawName)) {
        return PREDEFINED_ECLIPSE_PROFILES.get(rawName);
      }
      rawName = StringUtil.trimStart(rawName, "_");
    }
    return rawName;
  }

  @Nullable
  public CodeStyleSettings importCodeStyle() throws SchemeImportException {
    if (myCorePreferences != null) {
      CodeStyleSettings settings = new CodeStyleSettings();
      importProperties(myCorePreferences, settings);
      if (myUiPreferences != null && myImportOrganizeImportsConfig) {
        importOptimizeImportsSettings(myUiPreferences, settings);
      }
      return settings;
    }
    return null;
  }

  public boolean isEclipseImportsConfigAvailable() {
    return myUiPreferences != null && myUiPreferences.getProperty(OPTION_IMPORT_ORDER) != null;
  }

  public boolean isImportOrganizeImportsConfig() {
    return isEclipseImportsConfigAvailable() && myImportOrganizeImportsConfig;
  }

  public void setImportOrganizeImportsConfig(boolean importOrganizeImportsConfig) {
    myImportOrganizeImportsConfig = importOrganizeImportsConfig;
  }
}
