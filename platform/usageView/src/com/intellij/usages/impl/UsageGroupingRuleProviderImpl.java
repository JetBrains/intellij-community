// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewSettings;
import com.intellij.usages.impl.rules.ActiveRules;
import com.intellij.usages.impl.rules.DirectoryGroupingRule;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleProviderEx;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class UsageGroupingRuleProviderImpl implements UsageGroupingRuleProviderEx {
  protected boolean supportsNonCodeRule() {
    return true;
  }

  protected boolean supportsModuleRule() {
    return true;
  }

  protected boolean supportsScopesRule() {
    return true;
  }

  @Override
  public UsageGroupingRule @NotNull [] getActiveRules(@NotNull Project project) {
    return getActiveRules(project, UsageViewSettings.getInstance());
  }

  @Override
  public UsageGroupingRule @NotNull [] getActiveRules(@NotNull Project project, @NotNull UsageViewSettings usageViewSettings) {
    return ActiveRules.getActiveRules(project, usageViewSettings, supportsNonCodeRule(), supportsScopesRule(), supportsModuleRule());
  }

  @Override
  public @NotNull UsageGroupingRule[] getAllRules(@NotNull Project project, @Nullable UsageView usageView) {
    return ActiveRules.getAllRules(project, UsageViewSettings.getInstance(), supportsNonCodeRule(), supportsScopesRule(), supportsModuleRule());
  }

  @Override
  public AnAction @NotNull [] createGroupingActions(@NotNull UsageView view) {
    UsageViewImpl impl = (UsageViewImpl)view;

    AnAction groupByModuleTypeAction = supportsModuleRule() ? ActionManager.getInstance().getAction("UsageGrouping.Module") : null;

    AnAction groupByFileStructureAction = createGroupByFileStructureAction(impl);
    AnAction groupByDirectoryStructureAction = createGroupByDirectoryStructureAction();

    AnAction groupByScopeAction = supportsScopesRule() ? ActionManager.getInstance().getAction("UsageGrouping.Scope") : null;

    AnAction groupByPackageAction = ActionManager.getInstance().getAction(DirectoryGroupingRule.getInstance(((UsageViewImpl)view).getProject()).getGroupingActionId());

    ArrayList<AnAction> result = new ArrayList<>();

    if (view.getPresentation().isUsageTypeFilteringAvailable()) {
      AnAction groupByUsageTypeAction = ActionManager.getInstance().getAction("UsageGrouping.UsageType");

      ContainerUtil.addIfNotNull(result, groupByUsageTypeAction);
      ContainerUtil.addIfNotNull(result, groupByScopeAction);
      ContainerUtil.addIfNotNull(result, groupByModuleTypeAction);
      if (supportsModuleRule()) {
        AnAction flattenModulesAction = ActionManager.getInstance().getAction("UsageGrouping.FlattenModules");
        result.add(flattenModulesAction);
      }
      ContainerUtil.addIfNotNull(result, groupByPackageAction);
      ContainerUtil.addIfNotNull(result, groupByDirectoryStructureAction);
      ContainerUtil.addIfNotNull(result, groupByFileStructureAction);
    }
    else {
      ContainerUtil.addIfNotNull(result, groupByScopeAction);
      ContainerUtil.addIfNotNull(result, groupByModuleTypeAction);
      ContainerUtil.addIfNotNull(result, groupByPackageAction);
      ContainerUtil.addIfNotNull(result, groupByDirectoryStructureAction);
    }
    return result.toArray(AnAction.EMPTY_ARRAY);
  }

  /**
   * Deprecated. Get the action from ActionManager directly.
   */
  @Deprecated
  public static @NotNull GroupByFileStructureAction createGroupByFileStructureAction(UsageViewImpl impl) {
    return (GroupByFileStructureAction) ActionManager.getInstance().getAction("UsageGrouping.FileStructure");
  }

  private static @NotNull AnAction createGroupByDirectoryStructureAction() {
    return ActionManager.getInstance().getAction("UsageGrouping.DirectoryStructure");
  }

  @ApiStatus.Internal
  public static class GroupByFileStructureAction extends com.intellij.usages.impl.actions.GroupByFileStructureAction { }
}
