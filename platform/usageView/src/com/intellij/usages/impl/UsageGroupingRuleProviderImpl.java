// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewSettings;
import com.intellij.usages.impl.rules.ActiveRules;
import com.intellij.usages.impl.rules.DirectoryGroupingRule;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

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

  @Override
  public UsageGroupingRule @NotNull [] getActiveRules(@NotNull Project project) {
    return getActiveRules(project, UsageViewSettings.getInstance());
  }

  @Override
  public UsageGroupingRule @NotNull [] getActiveRules(@NotNull Project project, @NotNull UsageViewSettings usageViewSettings) {
    return ActiveRules.getActiveRules(project, usageViewSettings, supportsNonCodeRule(), supportsScopesRule(), supportsModuleRule());
  }

  @Override
  public AnAction @NotNull [] createGroupingActions(@NotNull UsageView view) {
    UsageViewImpl impl = (UsageViewImpl)view;
    JComponent component = impl.getComponent();

    GroupByModuleTypeAction groupByModuleTypeAction = supportsModuleRule() ? new GroupByModuleTypeAction(impl) : null;
    if (groupByModuleTypeAction != null) {
      KeyStroke stroke = SystemInfo.isMac
                         ? KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK)
                         : KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
      groupByModuleTypeAction.registerCustomShortcutSet(new CustomShortcutSet(stroke), component, impl);
    }

    RuleAction groupByFileStructureAction = createGroupByFileStructureAction(impl);
    RuleAction groupByDirectoryStructureAction = createGroupByDirectoryStructureAction(impl);

    GroupByScopeAction groupByScopeAction = supportsScopesRule() ? new GroupByScopeAction(impl) : null;

    GroupByPackageAction groupByPackageAction = new GroupByPackageAction(impl);
    KeyStroke stroke = SystemInfo.isMac
                       ? KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK)
                       : KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
    groupByPackageAction.registerCustomShortcutSet(new CustomShortcutSet(stroke), component, impl);

    ArrayList<AnAction> result = new ArrayList<>();

    if (view.getPresentation().isUsageTypeFilteringAvailable()) {
      GroupByUsageTypeAction groupByUsageTypeAction = new GroupByUsageTypeAction(impl);
      stroke = SystemInfo.isMac
               ? KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK)
               : KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
      groupByUsageTypeAction.registerCustomShortcutSet(new CustomShortcutSet(stroke), component, impl);

      ContainerUtil.addIfNotNull(result, groupByUsageTypeAction);
      ContainerUtil.addIfNotNull(result, groupByScopeAction);
      ContainerUtil.addIfNotNull(result, groupByModuleTypeAction);
      if (supportsModuleRule()) {
        FlattenModulesAction flattenModulesAction = new FlattenModulesAction(impl);
        stroke = SystemInfo.isMac
                 ? KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK)
                 : KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
        flattenModulesAction.registerCustomShortcutSet(new CustomShortcutSet(stroke), component, impl);
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

  public static @NotNull GroupByFileStructureAction createGroupByFileStructureAction(@NotNull UsageViewImpl impl) {
    JComponent component = impl.getComponent();
    GroupByFileStructureAction action = new GroupByFileStructureAction(impl);
    KeyStroke stroke = SystemInfo.isMac
                       ? KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK)
                       : KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
    action.registerCustomShortcutSet(new CustomShortcutSet(stroke), component, impl);
    return action;
  }

  private static @NotNull RuleAction createGroupByDirectoryStructureAction(@NotNull UsageViewImpl impl) {
    JComponent component = impl.getComponent();
    RuleAction action = new GroupByDirectoryStructureAction(impl);
    KeyStroke stroke = SystemInfo.isMac
                       ? KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK)
                       : KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK);
    action.registerCustomShortcutSet(new CustomShortcutSet(stroke), component, impl);
    return action;
  }

  private static class GroupByUsageTypeAction extends RuleAction {
    private GroupByUsageTypeAction(UsageViewImpl view) {
      super(view, UsageViewBundle.messagePointer("action.group.by.usage.type"), AllIcons.General.Filter); //TODO: special icon
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
      super(view, UsageViewBundle.messagePointer("action.group.by.test.production"), AllIcons.Actions.GroupByTestProduction);
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
      super(view, UsageViewBundle.messagePointer("action.group.by.module"), AllIcons.Actions.GroupByModule);
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
      super(view, UsageViewBundle.messagePointer("action.flatten.modules"), AllIcons.ObjectBrowser.FlattenModules);
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
      if (value) {
        myView.getUsageViewSettings().setGroupByDirectoryStructure(false); // mutually exclusive
      }
    }
  }

  private static class GroupByFileStructureAction extends RuleAction {
    private GroupByFileStructureAction(@NotNull UsageView view) {
      super(view, UsageViewBundle.messagePointer("action.group.by.file.structure"), AllIcons.Actions.GroupByMethod);
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
  private static class GroupByDirectoryStructureAction extends RuleAction {
    private GroupByDirectoryStructureAction(@NotNull UsageView view) {
      super(view, UsageViewBundle.messagePointer("action.group.by.directory.structure"), AllIcons.Actions.GroupByFile);
    }
    @Override
    protected boolean getOptionValue() {
      return myView.getUsageViewSettings().isGroupByDirectoryStructure();
    }
    @Override
    protected void setOptionValue(boolean value) {
      myView.getUsageViewSettings().setGroupByDirectoryStructure(value);
      if (value) {
        myView.getUsageViewSettings().setGroupByPackage(false); // mutually exclusive
      }
    }
  }
}
