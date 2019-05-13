/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.usages.impl;

import com.intellij.icons.AllIcons;
import com.intellij.usageView.UsageViewBundle;
import org.jetbrains.annotations.NotNull;

/**
* @author cdr
*/
class SortMembersAlphabeticallyAction extends RuleAction {
  SortMembersAlphabeticallyAction(@NotNull UsageViewImpl usageView) {
    super(usageView, UsageViewBundle.message("sort.alphabetically.action.text"), AllIcons.ObjectBrowser.Sorted);
  }

  @Override
  protected boolean getOptionValue() {
    return myView.getUsageViewSettings().isSortAlphabetically();
  }

  @Override
  protected void setOptionValue(final boolean value) {
    myView.getUsageViewSettings().setSortAlphabetically(value);
  }
}
