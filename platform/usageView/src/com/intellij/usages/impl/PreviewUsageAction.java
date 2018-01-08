/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.usages.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NotNull;

/**
* @author cdr
*/
class PreviewUsageAction extends RuleAction {
  PreviewUsageAction(@NotNull UsageView usageView) {
    super(usageView, UsageViewBundle.message("preview.usages.action.text", StringUtil.capitalize(StringUtil.pluralize(usageView.getPresentation().getUsagesWord()))), AllIcons.Actions.PreviewDetails);
  }

  @Override
  protected boolean getOptionValue() {
    return myView.getUsageViewSettings().isPreviewUsages();
  }

  @Override
  protected void setOptionValue(final boolean value) {
    myView.getUsageViewSettings().setPreviewUsages(value);
  }
}
