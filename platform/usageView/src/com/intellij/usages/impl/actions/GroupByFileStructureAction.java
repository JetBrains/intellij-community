// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.usageView.UsageViewBundle;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public
class GroupByFileStructureAction extends RuleAction {
  public GroupByFileStructureAction() {
    super(UsageViewBundle.messagePointer("action.group.by.file.structure"), AllIcons.Actions.GroupByMethod);
  }

  @Override
  protected boolean getOptionValue(AnActionEvent e) {
    return getUsageViewSettings(e).isGroupByFileStructure();
  }

  @Override
  protected void setOptionValue(AnActionEvent e, boolean value) {
    getUsageViewSettings(e).setGroupByFileStructure(value);
  }
}
