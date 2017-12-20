/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.usages.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewSettings;
import com.intellij.usages.impl.rules.*;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class UsageGroupingRuleProviderImpl implements UsageGroupingRuleProvider {
  protected boolean supportsNonCodeRule() {
    return true;
  }

  protected boolean supportsModuleRule() {
    return true;
  }

  protected boolean supportsScopesRule() {
    return true;
  }

  @NotNull
  @Override
  public UsageGroupingRule[] getActiveRules(@NotNull Project project) {
    return getActiveRules(project, UsageViewSettings.getInstance());
  }

  @Override
  @NotNull
  public UsageGroupingRule[] getActiveRules(@NotNull Project project, @NotNull UsageViewSettings usageViewSettings) {
    List<UsageGroupingRule> rules = new ArrayList<>();
    if (supportsNonCodeRule()) {
      rules.add(new NonCodeUsageGroupingRule(project));
    }
    if (supportsScopesRule() && usageViewSettings.isGroupByScope()) {
      rules.add(new UsageScopeGroupingRule());
    }
    if (usageViewSettings.isGroupByUsageType()) {
      rules.add(new UsageTypeGroupingRule());
    }
    if (supportsModuleRule() && usageViewSettings.isGroupByModule()) {
      rules.add(new ModuleGroupingRule(project, usageViewSettings.isFlattenModules()));
    }
    if (usageViewSettings.isGroupByPackage()) {
      rules.add(DirectoryGroupingRule.getInstance(project));
    }
    if (usageViewSettings.isGroupByFileStructure()) {
      FileStructureGroupRuleProvider[] providers = Extensions.getExtensions(FileStructureGroupRuleProvider.EP_NAME);
      for (FileStructureGroupRuleProvider ruleProvider : providers) {
        ContainerUtil.addIfNotNull(rules, ruleProvider.getUsageGroupingRule(project, usageViewSettings));
      }
    }
    else {
      rules.add(new FileGroupingRule(project));
    }

    return rules.toArray(new UsageGroupingRule[rules.size()]);
  }

  @Override
  @NotNull
  public AnAction[] createGroupingActions(UsageView view) {
    UsageViewImpl impl = (UsageViewImpl)view;
    JComponent component = impl.getComponent();

    GroupByModuleTypeAction groupByModuleTypeAction = supportsModuleRule() ? new GroupByModuleTypeAction(impl) : null;
    if (groupByModuleTypeAction != null) {
      groupByModuleTypeAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK)), component, impl);
    }

    GroupByFileStructureAction groupByFileStructureAction = createGroupByFileStructureAction(impl);

    GroupByScopeAction groupByScopeAction = supportsScopesRule() ? new GroupByScopeAction(impl) : null;

    GroupByPackageAction groupByPackageAction = new GroupByPackageAction(impl);
    groupByPackageAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK)), component, impl);

    ArrayList<AnAction> result = ContainerUtil.newArrayList();

    if (view.getPresentation().isUsageTypeFilteringAvailable()) {
      GroupByUsageTypeAction groupByUsageTypeAction = new GroupByUsageTypeAction(impl);
      groupByUsageTypeAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK)), component, impl);
      
      ContainerUtil.addIfNotNull(result, groupByUsageTypeAction);
      ContainerUtil.addIfNotNull(result, groupByScopeAction);
      ContainerUtil.addIfNotNull(result, groupByModuleTypeAction);
      if (supportsModuleRule()) {
        result.add(new FlattenModulesAction(impl));
      }
      ContainerUtil.addIfNotNull(result, groupByPackageAction);
      ContainerUtil.addIfNotNull(result, groupByFileStructureAction);
    }
    else {
      ContainerUtil.addIfNotNull(result, groupByScopeAction);
      ContainerUtil.addIfNotNull(result, groupByModuleTypeAction);
      ContainerUtil.addIfNotNull(result, groupByPackageAction);
      ContainerUtil.addIfNotNull(result, groupByFileStructureAction);
    }
    return result.toArray(new AnAction[result.size()]);
  }

  public static GroupByFileStructureAction createGroupByFileStructureAction(UsageViewImpl impl) {
    final JComponent component = impl.getComponent();
    final GroupByFileStructureAction groupByFileStructureAction = new GroupByFileStructureAction(impl);
    groupByFileStructureAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_M,
                                                                                                      InputEvent.CTRL_DOWN_MASK)), component,
                                                         impl);

    return groupByFileStructureAction;
  }

  private static class GroupByUsageTypeAction extends RuleAction {
    private GroupByUsageTypeAction(UsageViewImpl view) {
      super(view, UsageViewBundle.message("action.group.by.usage.type"), AllIcons.General.Filter); //TODO: special icon
    }
    @Override
    protected boolean getOptionValue() {
      return myView.getUsageViewSettings().isGroupByUsageType();
    }
    @Override
    protected void setOptionValue(boolean value) {
      myView.getUsageViewSettings().setGroupByUsageType(value);
    }
  }

  private static class GroupByScopeAction extends RuleAction {
    private GroupByScopeAction(UsageViewImpl view) {
      super(view, "Group by test/production", AllIcons.Actions.GroupByTestProduction);
    }
    @Override
    protected boolean getOptionValue() {
      return myView.getUsageViewSettings().isGroupByScope();
    }
    @Override
    protected void setOptionValue(boolean value) {
      myView.getUsageViewSettings().setGroupByScope(value);
    }
  }

  private static class GroupByModuleTypeAction extends RuleAction {
    private GroupByModuleTypeAction(UsageViewImpl view) {
      super(view, UsageViewBundle.message("action.group.by.module"), AllIcons.Actions.GroupByModule);
    }

    @Override
    protected boolean getOptionValue() {
      return myView.getUsageViewSettings().isGroupByModule();
    }

    @Override
    protected void setOptionValue(boolean value) {
      myView.getUsageViewSettings().setGroupByModule(value);
    }
  }

  private static class FlattenModulesAction extends RuleAction {
    private FlattenModulesAction(UsageViewImpl view) {
      super(view, UsageViewBundle.message("action.flatten.modules"), AllIcons.ObjectBrowser.FlattenModules);
    }

    @Override
    protected boolean getOptionValue() {
      return myView.getUsageViewSettings().isFlattenModules();
    }

    @Override
    protected void setOptionValue(boolean value) {
      myView.getUsageViewSettings().setFlattenModules(value);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(myView.getUsageViewSettings().isGroupByModule());
    }
  }

  private static class GroupByPackageAction extends RuleAction {
    private GroupByPackageAction(UsageViewImpl view) {
      super(view, DirectoryGroupingRule.getInstance(view.getProject()).getActionTitle(), AllIcons.Actions.GroupByPackage);
    }
    @Override
    protected boolean getOptionValue() {
      return myView.getUsageViewSettings().isGroupByPackage();
    }
    @Override
    protected void setOptionValue(boolean value) {
      myView.getUsageViewSettings().setGroupByPackage(value);
    }
  }

  private static class GroupByFileStructureAction extends RuleAction {
    private GroupByFileStructureAction(UsageViewImpl view) {
      super(view, UsageViewBundle.message("action.group.by.file.structure"), AllIcons.Actions.GroupByMethod);
    }
    @Override
    protected boolean getOptionValue() {
      return myView.getUsageViewSettings().isGroupByFileStructure();
    }
    @Override
    protected void setOptionValue(boolean value) {
      myView.getUsageViewSettings().setGroupByFileStructure(value);
    }
  }
}
