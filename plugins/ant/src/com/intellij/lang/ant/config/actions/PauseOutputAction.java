package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.config.execution.AntBuildMessageView;
import com.intellij.lang.ant.resources.AntBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.util.IconLoader;

public final class PauseOutputAction extends ToggleAction {
  private final AntBuildMessageView myAntBuildMessageView;

  public PauseOutputAction(AntBuildMessageView antBuildMessageView) {
    super(AntBundle.message("ant.view.pause.output.action.name"),null, IconLoader.getIcon("/actions/pause.png"));
    myAntBuildMessageView = antBuildMessageView;
  }

  public boolean isSelected(AnActionEvent event) {
    return myAntBuildMessageView.isOutputPaused();
  }

  public void setSelected(AnActionEvent event,boolean flag) {
    myAntBuildMessageView.setOutputPaused(flag);
  }

  public void update(AnActionEvent event){
    super.update(event);
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(!myAntBuildMessageView.isStopped() || isSelected(event));
  }
}

