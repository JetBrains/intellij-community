// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.fileEditor.FileEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

class MoveEditorToOppositeTabGroupAction extends AnAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  private final boolean closeSource;

  @SuppressWarnings("unused")
  MoveEditorToOppositeTabGroupAction() {
    this(true);
  }

  MoveEditorToOppositeTabGroupAction(boolean closeSource) {
    this.closeSource = closeSource;
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent event) {
    DataContext dataContext = event.getDataContext();
    VirtualFile vFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (vFile == null || project == null) {
      return;
    }

    EditorWindow window = EditorWindow.DATA_KEY.getData(dataContext);
    List<EditorWindow> siblings = window == null ? Collections.emptyList() : window.getSiblings$intellij_platform_ide_impl();
    if (siblings.size() != 1) {
      return;
    }

    EditorComposite editorComposite = window.getSelectedComposite();
    FileEntry entry = editorComposite == null ? null : editorComposite.currentStateAsFileEntry$intellij_platform_ide_impl();
    vFile.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, Boolean.TRUE);
    if (closeSource) {
      window.closeFile(vFile, true, false);
    }
    ((FileEditorManagerImpl)FileEditorManagerEx.getInstanceEx(project))
      .openFileImpl$intellij_platform_ide_impl(siblings.get(0), vFile, entry, new FileEditorOpenOptions().withRequestFocus());
    vFile.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, null);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    DataContext dataContext = e.getDataContext();
    VirtualFile vFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    EditorWindow window = EditorWindow.DATA_KEY.getData(dataContext);
    boolean enabled = isEnabled(vFile, window);
    presentation.setEnabled(enabled);
    if (e.isFromContextMenu()) {
      presentation.setVisible(enabled);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private boolean isEnabled(@Nullable VirtualFile vFile, @Nullable EditorWindow window) {
    if (vFile == null || window == null) {
      return false;
    }
    if (!closeSource && FileEditorManagerImpl.forbidSplitFor(vFile)) {
      return false;
    }
    return window.getSiblings$intellij_platform_ide_impl().size() == 1;
  }
}
