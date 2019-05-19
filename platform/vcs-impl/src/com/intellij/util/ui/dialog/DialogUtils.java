// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.dialog;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public final class DialogUtils {
  private DialogUtils() {}

  public static void invokeLaterAfterDialogShown(@NotNull DialogWrapper dialog, @NotNull Runnable action) {
    Application application = ApplicationManager.getApplication();
    if (application.isHeadlessEnvironment()) {
      // there's nothing to tie the action to, so we just schedule it as a next task
      application.invokeLater(action, ModalityState.any());
    }
    else {
      dialog.getWindow().addWindowListener(new WindowAdapter() {
        @Override
        public void windowOpened(WindowEvent e) {
          Window window = e.getWindow();
          application.invokeLater(action, ModalityState.stateForComponent(window));
          window.removeWindowListener(this);
        }
      });
    }
  }
}
