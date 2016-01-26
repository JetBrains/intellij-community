package com.intellij.openapi.progress.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.WindowManager;

import javax.swing.*;
import java.awt.*;

public class ProgressDialogFactoryImpl implements ProgressDialogFactory {
  @Override
  public ProgressDialogImpl createProgressDialog(ProgressWindow progressWindow, Project project, String cancelText, boolean shouldShowBackground, JComponent parentComponent) {
    Window window = WindowManager.getInstance().suggestParentWindow(project);

    Component parent = parentComponent;
    if (parent == null && project == null && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      parent = JOptionPane.getRootFrame();
    }

    final ProgressDialogImpl dialog;
    if (parent == null) {
      dialog = new ProgressDialogImpl(progressWindow, window, shouldShowBackground, project, cancelText);
    }
    else {
      dialog = new ProgressDialogImpl(progressWindow, window, shouldShowBackground, parent, cancelText);
    }

    Disposer.register(progressWindow, dialog);

    progressWindow.addStateDelegate(new AbstractProgressIndicatorExBase() {
      @Override
      public void cancel() {
        super.cancel();
        if (dialog != null) {
          dialog.cancel();
        }
      }
    });

    return dialog;
  }
}
