// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.impl.actions.RuleAction;
import org.jetbrains.annotations.NotNull;

class PreviewUsageAction extends RuleAction {

  PreviewUsageAction() {
    super(UsageViewBundle.messagePointer("preview.usages.action.text"), AllIcons.Actions.PreviewDetails);
  }

  @Override
  protected boolean getOptionValue(@NotNull AnActionEvent e) {
    UsageViewImpl impl = RuleAction.getUsageViewImpl(e);
    return impl != null && impl.isPreviewUsages();
  }

  @Override
  protected void setOptionValue(@NotNull AnActionEvent e, boolean value) {
    UsageViewImpl usageViewImpl = RuleAction.getUsageViewImpl(e);
    if (usageViewImpl != null) usageViewImpl.setPreviewUsages(value);
  }
}