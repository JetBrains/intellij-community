// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

import javax.swing.*;

public interface ProgressDialogFactory {
  class SERVICE {
    public static ProgressDialogFactory getInstance() {
      return ServiceManager.getService(ProgressDialogFactory.class);
    }
  }

  ProgressDialog createProgressDialog(ProgressWindow window, Project project, String cancelText, boolean shouldShowBackground, JComponent parentComponent);
}
