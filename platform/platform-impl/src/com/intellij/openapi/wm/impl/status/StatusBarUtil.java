// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.DockableEditorTabbedContainer;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kirill Likhodedov
 */
public final class StatusBarUtil {
  private static final Logger LOG = Logger.getInstance(StatusBar.class);

  private StatusBarUtil() { }

  @Nullable
  public static Editor getCurrentTextEditor(@Nullable StatusBar statusBar) {
    if (statusBar == null) return null;

    FileEditor fileEditor = getCurrentFileEditor(statusBar);
    if (fileEditor instanceof TextEditor) {
      Editor editor = ((TextEditor)fileEditor).getEditor();
      if (ensureValidEditorFile(editor)) {
        return editor;
      }
    }

    Project project = statusBar.getProject();
    if (project == null || project.isDisposed()) return null;

    FileEditorManager manager = FileEditorManager.getInstance(project);
    Editor editor = manager.getSelectedTextEditor();
    if (editor != null &&
        ensureValidEditorFile(editor) &&
        WindowManager.getInstance().getStatusBar(editor.getComponent(), project) == statusBar) {
      return editor;
    }
    return null;
  }

  /**
   * Finds the current file editor.
   */
  @Nullable
  public static FileEditor getCurrentFileEditor(@Nullable StatusBar statusBar) {
    if (statusBar == null) {
      return null;
    }

    Project project = statusBar.getProject();
    if (project == null) {
      return null;
    }

    if (LightEdit.owns(project)) {
      return LightEditService.getInstance().getSelectedFileEditor();
    }

    DockContainer c = DockManager.getInstance(project).getContainerFor(statusBar.getComponent());
    EditorsSplitters splitters = null;
    if (c instanceof DockableEditorTabbedContainer) {
      splitters = ((DockableEditorTabbedContainer)c).getSplitters();
    }

    if (splitters != null && splitters.getCurrentWindow() != null) {
      EditorWithProviderComposite editor = splitters.getCurrentWindow().getSelectedEditor();
      if (editor != null) {
        return editor.getSelectedWithProvider().getFileEditor();
      }
    }
    return null;
  }

  public static void setStatusBarInfo(@NotNull Project project, @NotNull @Nls String message) {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    if (statusBar != null) {
      statusBar.setInfo(message);
    }
  }

  private static boolean ensureValidEditorFile(@NotNull Editor editor) {
    Document document = editor.getDocument();
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file != null && !file.isValid()) {
      Document cachedDocument = FileDocumentManager.getInstance().getCachedDocument(file);
      Project project = editor.getProject();
      Boolean fileIsOpen = project == null ? null : ArrayUtil.contains(file, FileEditorManager.getInstance(project).getOpenFiles());
      LOG.error("Returned editor for invalid file: " + editor +
                "; disposed=" + editor.isDisposed() +
                "; file " + file.getClass() +
                "; cached document exists: " + (cachedDocument != null) +
                "; same as document: " + (cachedDocument == document) +
                "; file is open: " + fileIsOpen);
      return false;
    }
    return true;
  }
}
