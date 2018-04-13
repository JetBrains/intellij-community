// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
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

    ourDeprecatedIconsReplacements.put("/general/ideOptions.png", "AllIcons.General.Settings");
    ourDeprecatedIconsReplacements.put("/general/applicationSettings.png", "AllIcons.General.Settings");
    ourDeprecatedIconsReplacements.put("/vcs/customizeView.png", "AllIcons.General.Settings");

    ourDeprecatedIconsReplacements.put("/actions/refreshUsages.png", "AllIcons.Actions.Rerun");

    ourDeprecatedIconsReplacements.put("/compiler/error.png", "AllIcons.General.Error");
    ourDeprecatedIconsReplacements.put("/compiler/hideWarnings.png", "AllIcons.General.HideWarnings");
    ourDeprecatedIconsReplacements.put("/compiler/information.png", "AllIcons.General.Information");
    ourDeprecatedIconsReplacements.put("/compiler/warning.png", "AllIcons.General.Warning");
    ourDeprecatedIconsReplacements.put("/ide/errorSign.png", "AllIcons.General.Error");

    ourDeprecatedIconsReplacements.put("/actions/showSource.png", "AllIcons.Actions.Preview");
    ourDeprecatedIconsReplacements.put("/actions/consoleHistory.png", "AllIcons.General.MessageHistory");
    ourDeprecatedIconsReplacements.put("/vcs/messageHistory.png", "AllIcons.General.MessageHistory");

    register("AllIcons.Actions.Find", "/actions/findPlain", "/actions/menu_find");
    register("AllIcons.Actions.Help", "/actions/menu_help");
    register("AllIcons.Actions.Pause", "/debugger/threadStates/paused");
    register("AllIcons.Actions.Refresh", "/actions/sync", "/actions/synchronizeFS", "/vcs/refresh");
    register("AllIcons.Actions.StartDebugger", "/general/debug");
    register("AllIcons.Debugger.Watch", "/debugger/watches");
    register("AllIcons.General.Add", "/debugger/newWatch", "/toolbarDecorator/add");
    register("AllIcons.General.Filter", "/ant/filter", "/debugger/class_filter", "/inspector/useFilter");
    register("AllIcons.General.GearPlain", "/actions/showSettings", "/general/projectSettings", "/general/secondaryGroup");
    register("AllIcons.Nodes.Folder", "/nodes/treeClosed", "/nodes/treeOpen");
  }

  private static void register(@NotNull String id, @NotNull String... paths) {
    assert paths.length > 0 : "old paths are not specified";
    for (String path : paths) {
      assert null == ourDeprecatedIconsReplacements.put(path + ".png", id) : path + " is already registered";
      assert null == ourDeprecatedIconsReplacements.put(path + ".svg", id) : path + " is already registered";
    }
  }

  @Nullable
  @Override
  public String patchPath(String path) {
    return ourDeprecatedIconsReplacements.get(path);
  }

  @Nullable
  @Override
  public Class getContextClass(String path) {
    return DeprecatedDuplicatesIconPathPatcher.class;
  }
}
