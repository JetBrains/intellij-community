// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.usageView.UsageViewBundle;
import org.jetbrains.annotations.NotNull;

class FlattenModulesAction extends RuleAction {
  FlattenModulesAction() {
    super(UsageViewBundle.messagePointer("action.flatten.modules"), AllIcons.ObjectBrowser.FlattenModules);
  }

  @Override
  protected boolean getOptionValue(AnActionEvent e) {
    return getUsageViewSettings(e).isFlattenModules();
  }

  @Override
  protected void setOptionValue(AnActionEvent e, boolean value) {
    getUsageViewSettings(e).setFlattenModules(value);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    presentation.setEnabled(presentation.isEnabled() && getUsageViewSettings(e).isGroupByModule());
  }
}
