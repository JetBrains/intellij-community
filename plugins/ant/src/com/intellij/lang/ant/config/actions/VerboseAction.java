package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.execution.AntBuildMessageView;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.util.IconLoader;

public final class VerboseAction extends ToggleAction {
  private final AntBuildMessageView myAntBuildMessageView;

  public VerboseAction(AntBuildMessageView antBuildMessageView) {
    super(AntBundle.message("ant.verbose.show.all.messages.action.name"),
          AntBundle.message("ant.verbose.show.all.messages.action.description"), IconLoader.getIcon("/ant/verbose.png"));
    myAntBuildMessageView = antBuildMessageView;
  }

  public boolean isSelected(AnActionEvent event) {
    return myAntBuildMessageView.isVerboseMode();
  }

  public void setSelected(AnActionEvent event,boolean flag) {
    myAntBuildMessageView.setVerboseMode(flag);
  }
}
