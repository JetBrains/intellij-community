// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewPresentation;
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
  public @NotNull UsageGroupingRule @NotNull [] getActiveRules(
    @NotNull Project project,
    @NotNull UsageViewSettings usageViewSettings,
    @Nullable UsageViewPresentation presentation
  ) {
    return ActiveRules.getActiveRules(
      project, usageViewSettings, presentation, supportsNonCodeRule(), supportsScopesRule(), supportsModuleRule()
    );
  }

  @Override
  public @NotNull UsageGroupingRule @NotNull [] getAllRules(@NotNull Project project, @Nullable UsageView usageView) {
    return ActiveRules.getAllRules(
      project, UsageViewSettings.getInstance(),
      usageView == null ? null: usageView.getPresentation(),
      supportsNonCodeRule(), supportsScopesRule(), supportsModuleRule()
    );
  }

  @Override
  public AnAction @NotNull [] createGroupingActions(@NotNull UsageView view) {
    ActionManager actionManager = ActionManager.getInstance();
    AnAction groupByModuleTypeAction = supportsModuleRule() ? actionManager.getAction("UsageGrouping.Module") : null;
    AnAction flattenModulesAction = supportsModuleRule() ? actionManager.getAction("UsageGrouping.FlattenModules") : null;

    AnAction groupByFileStructureAction = actionManager.getAction("UsageGrouping.FileStructure");
    AnAction groupByDirectoryStructureAction = ActionManager.getInstance().getAction("UsageGrouping.DirectoryStructure");

    AnAction groupByScopeAction = supportsScopesRule() ? actionManager.getAction("UsageGrouping.Scope") : null;

    AnAction groupByPackageAction = actionManager.getAction(DirectoryGroupingRule.getInstance(((UsageViewImpl)view).getProject()).getGroupingActionId());

    ArrayList<AnAction> result = new ArrayList<>();

    if (view.getPresentation().isUsageTypeFilteringAvailable()) {
      AnAction groupByUsageTypeAction = actionManager.getAction("UsageGrouping.UsageType");

      ContainerUtil.addIfNotNull(result, groupByUsageTypeAction);
      ContainerUtil.addIfNotNull(result, groupByScopeAction);
      ContainerUtil.addIfNotNull(result, groupByModuleTypeAction);
      ContainerUtil.addIfNotNull(result, flattenModulesAction);
      ContainerUtil.addIfNotNull(result, groupByPackageAction);
      ContainerUtil.addIfNotNull(result, groupByDirectoryStructureAction);
      ContainerUtil.addIfNotNull(result, groupByFileStructureAction);
    }
    else {
      ContainerUtil.addIfNotNull(result, groupByScopeAction);
      ContainerUtil.addIfNotNull(result, groupByModuleTypeAction);
      ContainerUtil.addIfNotNull(result, flattenModulesAction);
      ContainerUtil.addIfNotNull(result, groupByPackageAction);
      ContainerUtil.addIfNotNull(result, groupByDirectoryStructureAction);
    }
    return result.toArray(AnAction.EMPTY_ARRAY);
  }

  /**
   * @deprecated Get the action from ActionManager directly.
   */
  @Deprecated
  public static @NotNull AnAction createGroupByFileStructureAction(UsageViewImpl impl) {
    return ActionManager.getInstance().getAction("UsageGrouping.FileStructure");
  }

  @ApiStatus.Internal
  public static class GroupByFileStructureAction extends com.intellij.usages.impl.actions.GroupByFileStructureAction { }
}
