package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.explorer.AntExplorer;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

public final class RemoveBuildFileAction extends AnAction {
  private final AntExplorer myAntExplorer;

  public RemoveBuildFileAction(AntExplorer antExplorer) {
    super(AntBundle.message("remove.build.file.action.name"));
    myAntExplorer = antExplorer;
  }

  public void actionPerformed(AnActionEvent e) {
    myAntExplorer.removeBuildFile();
  }
}
