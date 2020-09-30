// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Supplier;

public abstract class FileChooserAction extends AnAction implements DumbAware {
  protected FileChooserAction() {
    setEnabledInModalContext(true);
  }

  protected FileChooserAction(final @NlsActions.ActionText String text,
                              final @NlsActions.ActionDescription String description, final Icon icon) {
    this(() -> text, () -> description, icon);
  }

  protected FileChooserAction(@NotNull Supplier<String> text, @NotNull Supplier<String> description, final Icon icon) {
    super(text, description, icon);
    setEnabledInModalContext(true);
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    FileSystemTree tree = e.getData(FileSystemTree.DATA_KEY);
    if (tree != null) {
      actionPerformed(tree, e);
    }
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

  protected abstract void update(@NotNull FileSystemTree fileChooser, @NotNull AnActionEvent e);

  protected abstract void actionPerformed(@NotNull FileSystemTree fileChooser, @NotNull AnActionEvent e);
}