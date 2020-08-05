// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;

public final class StructuralSearchPlugin {
  private boolean searchInProgress;
  private DialogWrapper myDialog;

  public boolean isSearchInProgress() {
    return searchInProgress;
  }

  public void setSearchInProgress(boolean searchInProgress) {
    this.searchInProgress = searchInProgress;
  }

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
    return ServiceManager.getService(project, StructuralSearchPlugin.class);
  }
}
