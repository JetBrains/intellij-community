// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.importer;

import com.intellij.openapi.options.SchemeImportException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.PackageEntry;
import com.intellij.psi.codeStyle.PackageEntryTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class EclipseCodeStylePropertiesImporter implements EclipseFormatterOptions {

  public void importProperties(@NotNull Properties eclipseProperties, @NotNull CodeStyleSettings settings) throws SchemeImportException {
    Map<String, String> eclipseStringPropsMap = new HashMap<>();
    for (String key : eclipseProperties.stringPropertyNames()) {
      String value = eclipseProperties.getProperty(key);
      if (value != null) {
        eclipseStringPropsMap.put(key, value);
      }
    }
    EclipseCodeStyleSchemeImporter.importCodeStyleSettings(eclipseStringPropsMap, settings);
  }

  public void importOptimizeImportsSettings(@NotNull Properties uiPreferences, @NotNull CodeStyleSettings settings) {
    JavaCodeStyleSettings javaSettings = settings.getCustomSettings(JavaCodeStyleSettings.class);
    importOrderOfImports(uiPreferences, javaSettings);
    importStarImportThresholds(uiPreferences, javaSettings);
    javaSettings.LAYOUT_STATIC_IMPORTS_SEPARATELY = true;
    javaSettings.LAYOUT_ON_DEMAND_IMPORT_FROM_SAME_PACKAGE_FIRST = true;
    javaSettings.PACKAGES_TO_USE_IMPORT_ON_DEMAND.copyFrom(new PackageEntryTable());
  }

  private static void importOrderOfImports(@NotNull Properties uiPreferences, @NotNull JavaCodeStyleSettings javaSettings) {
    String oderOfImportsValue = uiPreferences.getProperty(OPTION_IMPORT_ORDER);
    if (oderOfImportsValue != null) {
      PackageEntryTable importLayoutTable = new PackageEntryTable();
      importLayoutTable.addEntry(PackageEntry.ALL_OTHER_STATIC_IMPORTS_ENTRY);
      String[] chunks = oderOfImportsValue.split(";");
      for (String importString : chunks) {
        if (!importString.trim().isEmpty()) {
          boolean isStatic = importString.startsWith("#");
          importString = StringUtil.trimStart(importString, "#");
          importLayoutTable.addEntry(PackageEntry.BLANK_LINE_ENTRY);
          importLayoutTable.addEntry(new PackageEntry(isStatic, importString, true));
        }
      }
      if (importLayoutTable.getEntryCount() > 0) {
        importLayoutTable.addEntry(PackageEntry.BLANK_LINE_ENTRY);
        importLayoutTable.addEntry(PackageEntry.ALL_OTHER_IMPORTS_ENTRY);
        javaSettings.getImportLayoutTable().copyFrom(importLayoutTable);
      }
    }
  }

  private static void importStarImportThresholds(@NotNull Properties uiPreferences, @NotNull JavaCodeStyleSettings javaSettings) {
    javaSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = parseThreshold(uiPreferences.getProperty(OPTION_ON_DEMAND_IMPORT_THRESHOLD));
    javaSettings.NAMES_COUNT_TO_USE_IMPORT_ON_DEMAND = parseThreshold(uiPreferences.getProperty(OPTION_ON_DEMAND_STATIC_IMPORT_THRESHOLD));
  }

  private static int parseThreshold(@Nullable String s) {
    if (s != null) {
      try {
        return Integer.parseInt(s);
      }
      catch (NumberFormatException nfe) {
        // ignore and return default
      }
    }
    return DEFAULT_IMPORTS_THRESHOLD;
  }
}
