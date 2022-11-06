// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.impl.BackgroundableActionLock;
import com.intellij.openapi.vcs.impl.VcsBackgroundableActions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class VcsAnnotateUtil {
  @NotNull
  public static List<Editor> getEditors(@NotNull Project project, @NotNull VirtualFile file) {
    FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(file);
    return ContainerUtil.mapNotNull(editors, fileEditor -> fileEditor instanceof TextEditor ? ((TextEditor)fileEditor).getEditor() : null);
  }

  @Nullable
  public static Editor getEditorFor(@NotNull VirtualFile file, @NotNull DataContext dataContext) {
    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    if (editor instanceof EditorEx &&
        file.equals(((EditorEx)editor).getVirtualFile())) {
      return editor;
    }

    return JBIterable.of(
        dataContext.getData(PlatformCoreDataKeys.FILE_EDITOR),
        dataContext.getData(PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR)
      )
      .filterNotNull()
      .filter(it -> file.equals(it.getFile()))
      .filter(TextEditor.class)
      .map(it -> it.getEditor())
      .first();
  }

  @NotNull
  public static BackgroundableActionLock getBackgroundableLock(@NotNull Project project, @NotNull VirtualFile file) {
    return BackgroundableActionLock.getLock(project, VcsBackgroundableActions.ANNOTATE, file);
  }
}
