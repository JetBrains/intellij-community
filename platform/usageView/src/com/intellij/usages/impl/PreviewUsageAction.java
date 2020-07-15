/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.usages.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.actions.RuleAction;
import org.jetbrains.annotations.NotNull;

class PreviewUsageAction extends RuleAction {
  PreviewUsageAction(@NotNull UsageView usageView) {
    super(UsageViewBundle.messagePointer("preview.usages.action.text", StringUtil.capitalize(StringUtil.pluralize(usageView.getPresentation().getUsagesWord()))), AllIcons.Actions.PreviewDetails);
  }

  @Override
  protected boolean getOptionValue(@NotNull AnActionEvent e) {
    UsageViewImpl impl = getUsageViewImpl(e);
    return impl != null && impl.isPreviewUsages();
  }

  @Override
  protected void setOptionValue(@NotNull AnActionEvent e, boolean value) {
    UsageViewImpl usageViewImpl = getUsageViewImpl(e);
    if (usageViewImpl != null) usageViewImpl.setPreviewUsages(value);
  }
}
