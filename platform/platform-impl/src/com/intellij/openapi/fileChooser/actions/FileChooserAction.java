// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class FileChooserAction extends AnAction implements DumbAware {
  protected FileChooserAction() {
    setEnabledInModalContext(true);
  }

  protected FileChooserAction(final String text, final String description, final Icon icon) {
    super(text, description, icon);
    setEnabledInModalContext(true);
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    FileSystemTree tree = e.getData(FileSystemTree.DATA_KEY);
    actionPerformed(tree, e);
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    FileSystemTree tree = e.getData(FileSystemTree.DATA_KEY);
    if (tree != null) {
      e.getPresentation().setEnabled(true);
      update(tree, e);
    }
    else {
      e.getPresentation().setEnabled(false);
    }
  }

  protected abstract void update(FileSystemTree fileChooser, AnActionEvent e);

  protected abstract void actionPerformed(FileSystemTree fileChooser, AnActionEvent e);
}