package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.execution.AntBuildMessageView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.IconLoader;

public final class ChangeViewAction extends AnAction {
  private final AntBuildMessageView myAntBuildMessageView;

  public ChangeViewAction(AntBuildMessageView antBuildMessageView) {
    super(AntBundle.message("ant.view.toggle.tree.text.action.name"),null, IconLoader.getIcon("/ant/changeView.png"));
    myAntBuildMessageView = antBuildMessageView;
  }

  public void actionPerformed(AnActionEvent e) {
    myAntBuildMessageView.changeView();
  }
}
