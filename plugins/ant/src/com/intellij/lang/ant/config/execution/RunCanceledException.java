package com.intellij.lang.ant.config.execution;

import com.intellij.execution.CantRunException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

public class RunCanceledException extends CantRunException {
  public RunCanceledException(String message) {
    super(message);
  }

  public void showMessage(Project project, String title) {
    Messages.showInfoMessage(project, getMessage(), title);
  }
}

