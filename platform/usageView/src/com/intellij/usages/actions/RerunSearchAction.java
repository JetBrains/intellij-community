// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.UsageViewImpl;
import org.jetbrains.annotations.NotNull;

/**
* @author gregsh
*/
public class RerunSearchAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    UsageView usageView = e.getData(UsageView.USAGE_VIEW_KEY);
    if (usageView instanceof UsageViewImpl) {
      ((UsageViewImpl)usageView).refreshUsages();
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    UsageView usageView = e.getData(UsageView.USAGE_VIEW_KEY);
    boolean enabled = usageView instanceof UsageViewImpl && ((UsageViewImpl)usageView).canPerformReRun();
    e.getPresentation().setEnabled(enabled);
  }
}
