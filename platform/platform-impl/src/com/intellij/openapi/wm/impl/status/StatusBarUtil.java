// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.DockableEditorTabbedContainer;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kirill Likhodedov
 */
public final class StatusBarUtil {
  private StatusBarUtil() { }

  public static @Nullable Editor getCurrentTextEditor(@Nullable StatusBar statusBar) {
    FileEditor fileEditor = getCurrentFileEditor(statusBar);
    if (fileEditor instanceof TextEditor) {
      Editor editor = ((TextEditor)fileEditor).getEditor();
      return ensureValidEditorFile(editor, fileEditor) && UIUtil.isShowing(editor.getComponent()) ? editor : null;
    }
    return null;
  }

  /**
   * Finds the current file editor.
   */
  public static @Nullable FileEditor getCurrentFileEditor(@Nullable StatusBar statusBar) {
    if (statusBar == null) {
      return null;
    }

    FileEditor currentEditor = statusBar.getCurrentEditor();
    if (currentEditor != null) {
      return currentEditor;
    }

    Project project = statusBar.getProject();
    if (project == null) {
      return null;
    }

    if (LightEdit.owns(project)) {
      return LightEditService.getInstance().getSelectedFileEditor();
    }

    EditorsSplitters splitters;
    if (statusBar == WindowManager.getInstance().getStatusBar(project)) {
      // main - avoid getting DockManager
      splitters = FileEditorManagerEx.getInstanceEx(project).getSplitters();
    }
    else {
      DockContainer dockContainer = DockManager.getInstance(project).getContainerFor(statusBar.getComponent(),
                                                                                     DockableEditorTabbedContainer.class::isInstance);
      if (dockContainer instanceof DockableEditorTabbedContainer) {
        splitters = ((DockableEditorTabbedContainer)dockContainer).getSplitters();
      }
      else {
        return null;
      }
    }

    if (splitters.getCurrentWindow() != null) {
      EditorComposite composite = splitters.getCurrentWindow().getSelectedComposite();
      if (composite != null) {
        return composite.getSelectedWithProvider().getFileEditor();
      }
    }
    return null;
  }

  public static void setStatusBarInfo(@NotNull Project project, @NotNull @NlsContexts.StatusBarText String message) {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(project);
    if (statusBar != null) {
      statusBar.setInfo(message);
    }
  }

  @SuppressWarnings("LoggerInitializedWithForeignClass")
  private static boolean ensureValidEditorFile(@NotNull Editor editor, @Nullable FileEditor fileEditor) {
    Document document = editor.getDocument();
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    if (file == null || file.isValid()) {
      return true;
    }

    Document cachedDocument = FileDocumentManager.getInstance().getCachedDocument(file);
    Project project = editor.getProject();
    Boolean fileIsOpen = project == null ? null : ArrayUtil.contains(file, FileEditorManager.getInstance(project).getOpenFiles());
    Logger.getInstance(StatusBar.class).error(
      "Returned editor for invalid file: " + editor +
      "; disposed=" + editor.isDisposed() +
      (fileEditor == null ? "" : "; fileEditor=" + fileEditor + "; fileEditor.valid=" + fileEditor.isValid()) +
      "; file " + file.getClass() +
      "; cached document exists: " + (cachedDocument != null) +
      "; same as document: " + (cachedDocument == document) +
      "; file is open: " + fileIsOpen
    );
    return false;
  }
}
