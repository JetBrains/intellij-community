// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.ide.DeleteProvider;
import com.intellij.ide.actions.DeleteAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.fileChooser.ex.FileChooserKeys;
import org.jetbrains.annotations.NotNull;

public class FileDeleteAction extends DeleteAction {
  public FileDeleteAction() {
    setEnabledInModalContext(true);
  }

  @Override
  protected DeleteProvider getDeleteProvider(DataContext dataContext) {
    return new VirtualFileDeleteProvider();
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    FileSystemTree tree = event.getData(FileSystemTree.DATA_KEY);
    if (tree == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    final Boolean available = event.getData(FileChooserKeys.DELETE_ACTION_AVAILABLE);
    if (available != null && !available) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    presentation.setEnabledAndVisible(true);
    super.update(event);
  }
}
