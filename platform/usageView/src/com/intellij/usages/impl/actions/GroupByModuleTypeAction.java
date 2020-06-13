// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.usageView.UsageViewBundle;

class GroupByModuleTypeAction extends RuleAction {
  GroupByModuleTypeAction() {
    super(UsageViewBundle.messagePointer("action.group.by.module"), AllIcons.Actions.GroupByModule);
  }

  @Override
  protected boolean getOptionValue(AnActionEvent e) {
    return getUsageViewSettings(e).isGroupByModule();
  }

  @Override
  protected void setOptionValue(AnActionEvent e, boolean value) {
    getUsageViewSettings(e).setGroupByModule(value);
  }
}
