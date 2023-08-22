// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.intellij.usages.rules.UsageFilteringRuleProvider.RULES_CHANGED;

/**
 * Groups by package
 */
class GroupByDirectoryAction extends RuleAction {
  GroupByDirectoryAction() {
    super(IdeBundle.message("action.title.group.by.directory"), AllIcons.Actions.GroupByPackage);
  }

  @Override
  protected boolean getOptionValue(@NotNull AnActionEvent e) {
    return getUsageViewSettings(e).isGroupByPackage();
  }

  @Override
  protected void setOptionValue(@NotNull AnActionEvent e, boolean value) {
    getUsageViewSettings(e).setGroupByPackage(value);
    if (value) {
      getUsageViewSettings(e).setGroupByDirectoryStructure(false); // mutually exclusive
    }
  }
}
