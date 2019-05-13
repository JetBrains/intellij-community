// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class EditorColorSchemesUsagesCollector extends ApplicationUsagesCollector {

  private final static int CURR_VERSION = 2;

  public static final String SCHEME_NAME_OTHER = "Other";
  public final static String[] KNOWN_NAMES = {
    "Default",
    "Darcula",
    "Obsidian",
    "Visual Studio",
    "Solarized",
    "Wombat",
    "Monkai",
    "XCode",
    "Sublime",
    "Oblivion",
    "Zenburn",
    "Cobalt",
    "Netbeans",
    "Eclipse",
    "Aptana",
    "Flash Builder",
    "IdeaLight",
    "High сontrast",
    "ReSharper",
    "Rider"
  };

  @Override
  public int getVersion() {
    return CURR_VERSION;
  }

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    return getDescriptors();
  }

  @NotNull
  public static Set<UsageDescriptor> getDescriptors() {
    EditorColorsScheme currentScheme = EditorColorsManager.getInstance().getGlobalScheme();
    Set<UsageDescriptor> usages = new HashSet<>();
    if (currentScheme instanceof AbstractColorsScheme) {
      String schemeName = currentScheme.getName();
      if (schemeName.startsWith(SchemeManager.EDITABLE_COPY_PREFIX)) {
        EditorColorsScheme original = ((AbstractColorsScheme)currentScheme).getOriginal();
        if (original != null) {
          schemeName = original.getName();
        }
      }
      final String reportableName = getKnownSchemeName(schemeName) + " (" + getLightDarkSuffix(currentScheme) + ")";
      usages.add(new UsageDescriptor(reportableName, 1));
    }
    return usages;
  }

  @NotNull
  private static String getLightDarkSuffix(@NotNull EditorColorsScheme scheme) {
    return ColorUtil.isDark(scheme.getDefaultBackground()) ? "Dark" : "Light";
  }

  @NotNull
  private static String getKnownSchemeName(@NonNls @NotNull String schemeName) {
    for (@NonNls String knownName : KNOWN_NAMES) {
      if (schemeName.toUpperCase().contains(knownName.toUpperCase())) {
        return knownName;
      }
    }
    return SCHEME_NAME_OTHER;
  }

  @NotNull
  @Override
  public String getGroupId() {
    return "ui.editor.color.schemes";
  }

  @Nullable
  @Override
  public FeatureUsageData getData() {
    return new FeatureUsageData().addOS();
  }
}
