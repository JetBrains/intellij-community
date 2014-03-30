/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.usages.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewSettings;
import com.intellij.usages.impl.rules.*;
import com.intellij.usages.rules.UsageGroupingRule;
import com.intellij.usages.rules.UsageGroupingRuleProvider;
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
  @Override
  @NotNull
  public UsageGroupingRule[] getActiveRules(Project project) {
    List<UsageGroupingRule> rules = new ArrayList<UsageGroupingRule>();
    rules.add(new NonCodeUsageGroupingRule(project));
    if (UsageViewSettings.getInstance().GROUP_BY_SCOPE) {
      rules.add(new UsageScopeGroupingRule());
    }
    if (UsageViewSettings.getInstance().GROUP_BY_USAGE_TYPE) {
      rules.add(new UsageTypeGroupingRule());
    }
    if (UsageViewSettings.getInstance().GROUP_BY_MODULE) {
      rules.add(new ModuleGroupingRule());
    }
    if (UsageViewSettings.getInstance().GROUP_BY_PACKAGE) {
      rules.add(DirectoryGroupingRule.getInstance(project));
    }
    if (UsageViewSettings.getInstance().GROUP_BY_FILE_STRUCTURE) {
      FileStructureGroupRuleProvider[] providers = Extensions.getExtensions(FileStructureGroupRuleProvider.EP_NAME);
      for (FileStructureGroupRuleProvider ruleProvider : providers) {
        final UsageGroupingRule rule = ruleProvider.getUsageGroupingRule(project);
        if(rule != null) {
          rules.add(rule);
        }
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
    final UsageViewImpl impl = (UsageViewImpl)view;
    final JComponent component = impl.getComponent();

    final GroupByModuleTypeAction groupByModuleTypeAction = new GroupByModuleTypeAction(impl);
    groupByModuleTypeAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK)), component, impl);

    final GroupByFileStructureAction groupByFileStructureAction = createGroupByFileStructureAction(impl);

    final GroupByScopeAction groupByScopeAction = new GroupByScopeAction(impl);

    final GroupByPackageAction groupByPackageAction = new GroupByPackageAction(impl);
    groupByPackageAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK)), component, impl);

    if(view.getPresentation().isCodeUsages()) {
      final GroupByUsageTypeAction groupByUsageTypeAction = new GroupByUsageTypeAction(impl);
      groupByUsageTypeAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK)), component, impl);
      return new AnAction[] {
        groupByUsageTypeAction,
        groupByScopeAction,
        groupByModuleTypeAction,
        groupByPackageAction,
        groupByFileStructureAction,
      };
    }
    else {
      return new AnAction[] {
        groupByScopeAction,
        groupByModuleTypeAction,
        groupByPackageAction,
        groupByFileStructureAction,
      };
    }
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
      return UsageViewSettings.getInstance().GROUP_BY_USAGE_TYPE;
    }
    @Override
    protected void setOptionValue(boolean value) {
      UsageViewSettings.getInstance().GROUP_BY_USAGE_TYPE = value;
    }
  }

  private static class GroupByScopeAction extends RuleAction {
    private GroupByScopeAction(UsageViewImpl view) {
      super(view, "Group by test/production", AllIcons.Actions.GroupByTestProduction);
    }
    @Override
    protected boolean getOptionValue() {
      return UsageViewSettings.getInstance().GROUP_BY_SCOPE;
    }
    @Override
    protected void setOptionValue(boolean value) {
      UsageViewSettings.getInstance().GROUP_BY_SCOPE = value;
    }
  }

  private static class GroupByModuleTypeAction extends RuleAction {
    private GroupByModuleTypeAction(UsageViewImpl view) {
      super(view, UsageViewBundle.message("action.group.by.module"), AllIcons.Actions.GroupByModule);
    }

    @Override
    protected boolean getOptionValue() {
      return UsageViewSettings.getInstance().GROUP_BY_MODULE;
    }

    @Override
    protected void setOptionValue(boolean value) {
      UsageViewSettings.getInstance().GROUP_BY_MODULE = value;
    }
  }

  private static class GroupByPackageAction extends RuleAction {
    private GroupByPackageAction(UsageViewImpl view) {
      super(view, DirectoryGroupingRule.getInstance(view.getProject()).getActionTitle(), AllIcons.Actions.GroupByPackage);
    }
    @Override
    protected boolean getOptionValue() {
      return UsageViewSettings.getInstance().GROUP_BY_PACKAGE;
    }
    @Override
    protected void setOptionValue(boolean value) {
      UsageViewSettings.getInstance().GROUP_BY_PACKAGE = value;
    }
  }

  private static class GroupByFileStructureAction extends RuleAction {
    private GroupByFileStructureAction(UsageViewImpl view) {
      super(view, UsageViewBundle.message("action.group.by.file.structure"), AllIcons.Actions.GroupByMethod);
    }
    @Override
    protected boolean getOptionValue() {
      return UsageViewSettings.getInstance().GROUP_BY_FILE_STRUCTURE;
    }
    @Override
    protected void setOptionValue(boolean value) {
      UsageViewSettings.getInstance().GROUP_BY_FILE_STRUCTURE = value;
    }
  }
}
