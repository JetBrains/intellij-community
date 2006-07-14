package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildListener;
import com.intellij.lang.ant.config.execution.AntBuildMessageView;
import com.intellij.lang.ant.config.execution.ExecutionHandler;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public final class RunAction extends AnAction {
  private final AntBuildMessageView myAntBuildMessageView;
  public static final Icon RERUN_ICON = IconLoader.getIcon("/actions/refreshUsages.png");

  public RunAction(AntBuildMessageView antBuildMessageView) {
    super(AntBundle.message("rerun.ant.action.name"), null, RERUN_ICON);
    myAntBuildMessageView = antBuildMessageView;
  }

  public void actionPerformed(AnActionEvent e) {
    ExecutionHandler.runBuild(
      myAntBuildMessageView.getBuildFile(),
      myAntBuildMessageView.getTargets(),
      myAntBuildMessageView,
      e.getDataContext(), AntBuildListener.NULL);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(myAntBuildMessageView.isStopped());
  }
}
