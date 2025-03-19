// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.actions;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildListener;
import com.intellij.lang.ant.config.execution.AntBuildMessageView;
import com.intellij.lang.ant.config.execution.ExecutionHandler;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;

public final class RunAction extends AnAction {
  private final AntBuildMessageView myAntBuildMessageView;

  public RunAction(AntBuildMessageView antBuildMessageView) {
    super(AntBundle.message("rerun.ant.action.name"), null, AllIcons.Actions.Rerun);
    myAntBuildMessageView = antBuildMessageView;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ExecutionHandler.runBuild(
      myAntBuildMessageView.getBuildFile(),
      myAntBuildMessageView.getTargets(),
      myAntBuildMessageView,
      e.getDataContext(), myAntBuildMessageView.getAdditionalProperties(), AntBuildListener.NULL);
  }

  @Override
  public void update(@NotNull AnActionEvent event){
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(myAntBuildMessageView.isStopped());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
