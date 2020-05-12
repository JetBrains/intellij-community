// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.beans.MetricEventFactoryKt;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class EditorColorSchemesUsagesCollector extends ApplicationUsagesCollector {

  private final static int CURR_VERSION = 3;

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
    "High contrast",
    "ReSharper",
    "Rider"
  };

  @Override
  public int getVersion() {
    return CURR_VERSION;
  }

  @NotNull
  @Override
  public Set<MetricEvent> getMetrics() {
    EditorColorsScheme currentScheme = EditorColorsManager.getInstance().getGlobalScheme();
    Set<MetricEvent> usages = new HashSet<>();
    if (currentScheme instanceof AbstractColorsScheme) {
      String schemeName = currentScheme.getName();
      if (schemeName.startsWith(Scheme.EDITABLE_COPY_PREFIX)) {
        EditorColorsScheme original = ((AbstractColorsScheme)currentScheme).getOriginal();
        if (original != null) {
          schemeName = original.getName();
        }
      }
      final FeatureUsageData data = new FeatureUsageData().
        addData("scheme", getKnownSchemeName(schemeName)).
        addData("is_dark", ColorUtil.isDark(currentScheme.getDefaultBackground()));
      usages.add(MetricEventFactoryKt.newMetric("enabled.color.scheme", data));
    }
    return usages;
  }

  @NotNull
  private static String getKnownSchemeName(@NonNls @NotNull String schemeName) {
    for (@NonNls String knownName : KNOWN_NAMES) {
      if (StringUtil.toUpperCase(schemeName).contains(StringUtil.toUpperCase(knownName))) {
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
}
