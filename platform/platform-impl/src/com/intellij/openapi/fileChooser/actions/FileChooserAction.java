// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserPanel;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class FileChooserAction extends AnAction implements DumbAware {
  protected FileChooserAction() {
    setEnabledInModalContext(true);
  }

  protected FileChooserAction(@NlsActions.ActionText String text, @NlsActions.ActionDescription String description, Icon icon) {
    super(text, description, icon);
    setEnabledInModalContext(true);
  }

  @Override
  public final void update(@NotNull AnActionEvent e) {
    FileChooserPanel panel;
    FileSystemTree tree;
    if ((panel = e.getData(FileChooserPanel.DATA_KEY)) != null) {
      e.getPresentation().setEnabled(true);
      update(panel, e);
    }
    else if ((tree = e.getData(FileSystemTree.DATA_KEY)) != null) {
      e.getPresentation().setEnabled(true);
      update(tree, e);
    }
    else {
      e.getPresentation().setEnabledAndVisible(false);
    }
  }

  @Override
  public final void actionPerformed(@NotNull AnActionEvent e) {
    FileChooserPanel panel;
    FileSystemTree tree;
    if ((panel = e.getData(FileChooserPanel.DATA_KEY)) != null) {
      actionPerformed(panel, e);
    }
    else if ((tree = e.getData(FileSystemTree.DATA_KEY)) != null) {
      actionPerformed(tree, e);
    }
  }

  protected void update(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(false);
  }

  protected void actionPerformed(@NotNull FileChooserPanel panel, @NotNull AnActionEvent e) { }

  protected abstract void update(@NotNull FileSystemTree fileChooser, @NotNull AnActionEvent e);

  protected abstract void actionPerformed(@NotNull FileSystemTree fileChooser, @NotNull AnActionEvent e);
}
