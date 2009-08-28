package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;

/**
 * @author yole
 */
public class RestartInfoAction extends AnAction implements DumbAware {
  public void actionPerformed(AnActionEvent e) {
    final Application app = ApplicationManager.getApplication();
    if (app.isRestartCapable()) {
      app.restart();
    }
  }
}
