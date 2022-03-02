// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;

public final class StructuralSearchPlugin {
  private DialogWrapper myDialog;

  public DialogWrapper getDialog() {
    return myDialog;
  }

  public void setDialog(DialogWrapper dialog) {
    if (dialog != null && (dialog.isDisposed() || dialog.isModal())) {
      return;
    }
    myDialog = dialog;
  }

  public static StructuralSearchPlugin getInstance(@NotNull Project project) {
    return project.getService(StructuralSearchPlugin.class);
  }
}
