package com.intellij.cvsSupport2.actions;

import com.intellij.cvsSupport2.actions.cvsContext.CvsContext;
import com.intellij.cvsSupport2.actions.cvsContext.CvsContextWrapper;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;

public abstract class CvsGlobalAction extends AnAction {
  public void update(AnActionEvent e) {
    CvsContext cvsContext = CvsContextWrapper.createInstance(e);
    Presentation presentation = e.getPresentation();
    if (cvsContext.cvsIsActive()) {
      presentation.setVisible(true);
      presentation.setEnabled(true);
    }
    else {
      presentation.setVisible(false);
      presentation.setEnabled(false);
    }
  }
}