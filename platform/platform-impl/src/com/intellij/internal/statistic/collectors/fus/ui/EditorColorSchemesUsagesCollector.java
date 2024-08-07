// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.ui;

import com.intellij.internal.statistic.beans.MetricEvent;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId2;
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
import java.util.List;
import java.util.Set;

final class EditorColorSchemesUsagesCollector extends ApplicationUsagesCollector {

  private static final int CURR_VERSION = 8;

  public static final String SCHEME_NAME_OTHER = "Other";
  public static final String[] KNOWN_NAMES = {
    "Default",
    "Darcula Contrast",
    "Darcula",
    "Obsidian",
    "Visual Studio",
    "Solarized",
    "Wombat",
    "Monokai",
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
    "Rider",
    "IntelliJ Light",
    "Light",
    "Dark",
    SCHEME_NAME_OTHER
  };

  private static final EventLogGroup GROUP = new EventLogGroup("ui.editor.color.schemes", CURR_VERSION);
  private static final EventId2<String, Boolean> COLOR_SCHEME =
    GROUP.registerEvent(
      "enabled.color.scheme",
      EventFields.String("scheme", List.of(KNOWN_NAMES)),
      EventFields.Boolean("is_dark")
    );

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }

  @Override
  public @NotNull Set<MetricEvent> getMetrics() {
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
      String scheme = getKnownSchemeName(schemeName);
      boolean isDark = ColorUtil.isDark(currentScheme.getDefaultBackground());
      usages.add(COLOR_SCHEME.metric(scheme, isDark));
    }
    return usages;
  }

  private static @NotNull String getKnownSchemeName(@NonNls @NotNull String schemeName) {
    for (@NonNls String knownName : KNOWN_NAMES) {
      if (StringUtil.toUpperCase(schemeName).contains(StringUtil.toUpperCase(knownName))) {
        return knownName;
      }
    }
    return SCHEME_NAME_OTHER;
  }
}
