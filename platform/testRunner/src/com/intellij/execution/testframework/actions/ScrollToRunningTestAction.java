// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class ScrollToRunningTestAction extends AnAction implements DumbAware {
  private @Nullable TestFrameworkRunningModel myModel;

  public ScrollToRunningTestAction() {
    super(ExecutionBundle.messagePointer("junit.running.info.scroll.to.running.test.action.name"),
          ExecutionBundle.messagePointer("junit.running.info.scroll.to.running.test.action.description"),
          AllIcons.General.Locate);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean tracked = myModel != null && TestConsoleProperties.TRACK_RUNNING_TEST.value(myModel.getProperties());
    e.getPresentation().setVisible(tracked);
    e.getPresentation().setEnabled(tracked && myModel.isRunning());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (myModel != null) {
      myModel.scrollToRunningTest();
    }
  }

  public void setModel(@Nullable TestFrameworkRunningModel model) {
    myModel = model;
  }
}
