package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.execution.AntBuildMessageView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.IconLoader;

public final class StopAction extends AnAction {
  private final AntBuildMessageView myAntBuildMessageView;

  public StopAction(AntBuildMessageView antBuildMessageView) {
    super(AntBundle.message("stop.ant.action.name"),null, IconLoader.getIcon("/actions/suspend.png"));
    myAntBuildMessageView = antBuildMessageView;
  }

  public void actionPerformed(AnActionEvent e) {
    myAntBuildMessageView.stopProcess();
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(!myAntBuildMessageView.isStopped());
  }
}
