// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MoveEditorToOppositeTabGroupAction extends AnAction implements DumbAware {
  private final boolean myCloseSource;

  @SuppressWarnings("unused")
  MoveEditorToOppositeTabGroupAction() {
    this(true);
  }

  public MoveEditorToOppositeTabGroupAction(boolean closeSource) {
    myCloseSource = closeSource;
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    final VirtualFile vFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (vFile == null || project == null) {
      return;
    }

    EditorWindow window = EditorWindow.DATA_KEY.getData(dataContext);
    if (window == null) {
      return;
    }

    List<EditorWindow> siblings = window.getSiblings();
    if (siblings.size() == 1) {
      EditorComposite editorComposite = window.getSelectedComposite();
      HistoryEntry entry = editorComposite.currentStateAsHistoryEntry();
      vFile.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, Boolean.TRUE);
      if (myCloseSource) {
        window.closeFile(vFile, true, false);
      }
      ((FileEditorManagerImpl)FileEditorManagerEx.getInstanceEx(project)).openMaybeInvalidFile$intellij_platform_ide_impl(siblings.get(0), vFile, entry);
      vFile.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, null);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final DataContext dataContext = e.getDataContext();
    final VirtualFile vFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    final EditorWindow window = EditorWindow.DATA_KEY.getData(dataContext);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      presentation.setVisible(isEnabled(vFile, window));
    }
    else {
      presentation.setEnabled(isEnabled(vFile, window));
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
    if (!myCloseSource && FileEditorManagerImpl.forbidSplitFor(vFile)) {
      return false;
    }
    return window.getSiblings().size() == 1;
  }
}
