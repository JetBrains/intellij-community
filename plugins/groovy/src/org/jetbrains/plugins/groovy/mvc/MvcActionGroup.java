package org.jetbrains.plugins.groovy.mvc;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Pair;

public class MvcActionGroup extends DefaultActionGroup implements DumbAware {

  @Override
  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    Pair<MvcFramework, Module> pair = MvcActionBase.guessFramework(e);

    if (pair != null) {
      presentation.setVisible(true);
      presentation.setText(pair.getFirst().getDisplayName());
      presentation.setIcon(pair.getFirst().getIcon());
    }
    else {
      presentation.setVisible(false);
    }
  }
}
