// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
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
  public static @NotNull List<Editor> getEditors(@NotNull Project project, @NotNull VirtualFile file) {
    List<FileEditor> editors = FileEditorManager.getInstance(project).getEditorList(file);
    return ContainerUtil.mapNotNull(editors, fileEditor -> fileEditor instanceof TextEditor ? ((TextEditor)fileEditor).getEditor() : null);
  }

  public static @Nullable Editor getEditorFor(@NotNull VirtualFile file, @NotNull DataContext dataContext) {
    Editor editor = dataContext.getData(CommonDataKeys.EDITOR);
    if (editor != null && isEditorForFile(editor, file)) {
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

  public static boolean isEditorForFile(@NotNull Editor editor, @NotNull VirtualFile file) {
    // Editor.getVirtualFile is not being set for many editors
    Document document = FileDocumentManager.getInstance().getCachedDocument(file);
    return editor.getDocument().equals(document);
  }

  public static @NotNull BackgroundableActionLock getBackgroundableLock(@NotNull Project project, @NotNull VirtualFile file) {
    return BackgroundableActionLock.getLock(project, VcsBackgroundableActions.ANNOTATE, file);
  }
}
