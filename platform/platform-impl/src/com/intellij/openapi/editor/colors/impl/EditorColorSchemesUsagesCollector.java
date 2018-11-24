/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.editor.colors.impl;

import com.intellij.internal.statistic.CollectUsagesException;
import com.intellij.internal.statistic.UsagesCollector;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.options.SchemeManager;
import com.intellij.ui.ColorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class EditorColorSchemesUsagesCollector extends UsagesCollector {

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
  public Set<UsageDescriptor> getUsages() throws CollectUsagesException {
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
        schemeName += " (" + (isDark ? "Dark" : "Light") + ")";
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
  public GroupDescriptor getGroupId() {
    return GroupDescriptor.create(GROUP_ID);
  }

}
