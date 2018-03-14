// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui;

import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.impl.AbstractColorsScheme;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.ui.ColorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class EditorColorSchemesUsagesCollector extends ApplicationUsagesCollector {

  public static final String GROUP_ID = "Color Schemes";
  public static final String SCHEME_NAME_OTHER = "Other";
  public final static String[] KNOWN_NAMES = {
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
    "Flash Builder"
  };

  @NotNull
  @Override
  public Set<UsageDescriptor> getUsages() {
    return getDescriptors();
  }

  @NotNull
  public static Set<UsageDescriptor> getDescriptors() {
    EditorColorsScheme currentScheme = EditorColorsManager.getInstance().getGlobalScheme();
    Set<UsageDescriptor> usages = ContainerUtil.newHashSet();
    String schemeName = SCHEME_NAME_OTHER;
    if (currentScheme instanceof AbstractColorsScheme) {
      String name = currentScheme.getName();
      if (name.startsWith(SchemeManager.EDITABLE_COPY_PREFIX)) {
        EditorColorsScheme original = ((AbstractColorsScheme)currentScheme).getOriginal();
        if (original != null) {
          schemeName = original.getName();
        }
      }
      if (schemeName == SCHEME_NAME_OTHER) {
        String knownName = getKnownSchemeName(name);
        if (knownName != null) {
          schemeName = knownName;
        }
        boolean isDark = ColorUtil.isDark(currentScheme.getDefaultBackground());
        schemeName += "[" + (isDark ? "Dark" : "Light") + "]";
      }
      usages.add(new UsageDescriptor(schemeName, 1));
    }
    return usages;
  }

  @Nullable
  private static String getKnownSchemeName(@NonNls @NotNull String schemeName) {
    for (@NonNls String knownName : KNOWN_NAMES) {
      if (schemeName.toUpperCase().contains(knownName.toUpperCase())) {
        return knownName;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public String getGroupId() {
    return "statistics.ui.editor.color.schemes";
  }

}
