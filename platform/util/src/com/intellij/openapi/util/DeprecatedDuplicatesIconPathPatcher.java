/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class DeprecatedDuplicatesIconPathPatcher extends IconPathPatcher {
  @NonNls private static final Map<String, String> ourDeprecatedIconsReplacements = new HashMap<String, String>();

  static {
    ourDeprecatedIconsReplacements.put("/general/toolWindowDebugger.png", "AllIcons.Toolwindows.ToolWindowDebugger");
    ourDeprecatedIconsReplacements.put("/general/toolWindowChanges.png", "AllIcons.Toolwindows.ToolWindowChanges");

    ourDeprecatedIconsReplacements.put("/actions/showSettings.png", "AllIcons.General.ProjectSettings");
    ourDeprecatedIconsReplacements.put("/general/ideOptions.png", "AllIcons.General.Settings");
    ourDeprecatedIconsReplacements.put("/general/applicationSettings.png", "AllIcons.General.Settings");
    ourDeprecatedIconsReplacements.put("/toolbarDecorator/add.png", "AllIcons.General.Add");
    ourDeprecatedIconsReplacements.put("/vcs/customizeView.png", "AllIcons.General.Settings");

    ourDeprecatedIconsReplacements.put("/vcs/refresh.png", "AllIcons.Actions.Refresh");
    ourDeprecatedIconsReplacements.put("/actions/sync.png", "AllIcons.Actions.Refresh");
    ourDeprecatedIconsReplacements.put("/actions/refreshUsages.png", "AllIcons.Actions.Rerun");

    ourDeprecatedIconsReplacements.put("/compiler/error.png", "AllIcons.General.Error");
    ourDeprecatedIconsReplacements.put("/compiler/hideWarnings.png", "AllIcons.General.HideWarnings");
    ourDeprecatedIconsReplacements.put("/compiler/information.png", "AllIcons.General.Information");
    ourDeprecatedIconsReplacements.put("/compiler/warning.png", "AllIcons.General.Warning");
    ourDeprecatedIconsReplacements.put("/ide/errorSign.png", "AllIcons.General.Error");

    ourDeprecatedIconsReplacements.put("/ant/filter.png", "AllIcons.General.Filter");
    ourDeprecatedIconsReplacements.put("/inspector/useFilter.png", "AllIcons.General.Filter");

    ourDeprecatedIconsReplacements.put("/actions/showSource.png", "AllIcons.Actions.Preview");
    ourDeprecatedIconsReplacements.put("/actions/consoleHistory.png", "AllIcons.General.MessageHistory");
    ourDeprecatedIconsReplacements.put("/vcs/messageHistory.png", "AllIcons.General.MessageHistory");
  }

  @Nullable
  @Override
  public String patchPath(String path) {
    return ourDeprecatedIconsReplacements.get(path);
  }
}
