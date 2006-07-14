package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.config.explorer.AntExplorer;
import com.intellij.lang.ant.resources.AntBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.util.IconLoader;

public final class AntBuildFilePropertiesAction extends AnAction {
  private final AntExplorer myAntExplorer;

  public AntBuildFilePropertiesAction(AntExplorer antExplorer) {
    super(AntBundle.message("build.file.properties.action.name"),
          AntBundle.message("build.file.properties.action.description"),
          IconLoader.getIcon("/ant/properties.png"));
    myAntExplorer = antExplorer;
    registerCustomShortcutSet(CommonShortcuts.ALT_ENTER, myAntExplorer);
  }

  public void actionPerformed(AnActionEvent e) {
    myAntExplorer.setBuildFileProperties(e.getDataContext());
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(myAntExplorer.isBuildFileSelected());
  }
}
