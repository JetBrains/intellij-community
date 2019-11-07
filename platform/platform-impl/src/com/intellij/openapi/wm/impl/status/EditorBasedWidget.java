// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ArrayUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public abstract class EditorBasedWidget implements StatusBarWidget, FileEditorManagerListener {
  private static final Logger LOG = Logger.getInstance(EditorBasedWidget.class);
  public static final String SWING_FOCUS_OWNER_PROPERTY = "focusOwner";

  @NotNull
  protected final Project myProject;

  protected StatusBar myStatusBar;
  protected MessageBusConnection myConnection;
  private volatile boolean myDisposed;

  protected EditorBasedWidget(@NotNull Project project) {
    myProject = project;
  }

  @Nullable
  protected final Editor getEditor() {
    final Project project = getProject();
    if (project.isDisposed()) return null;

    FileEditor fileEditor = StatusBarUtil.getCurrentFileEditor(project, myStatusBar);
    Editor result = null;
    if (fileEditor instanceof TextEditor) {
      Editor editor = ((TextEditor)fileEditor).getEditor();
      if (ensureValidEditorFile(editor)) {
        result = editor;
      }
    }

    if (result == null) {
      final FileEditorManager manager = FileEditorManager.getInstance(project);
      Editor editor = manager.getSelectedTextEditor();
      if (editor != null &&
          WindowManager.getInstance().getStatusBar(editor.getComponent(), project) == myStatusBar &&
          ensureValidEditorFile(editor)) {
        result = editor;
      }
    }

    return result;
  }

  private static boolean ensureValidEditorFile(Editor editor) {
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

  boolean isOurEditor(Editor editor) {
    return editor != null &&
           editor.getComponent().isShowing() &&
           !Boolean.TRUE.equals(editor.getUserData(EditorTextField.SUPPLEMENTARY_KEY)) &&
           WindowManager.getInstance().getStatusBar(editor.getComponent(), editor.getProject()) == myStatusBar;
  }

  Component getFocusedComponent() {
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focusOwner == null) {
      IdeFocusManager focusManager = IdeFocusManager.getInstance(myProject);
      IdeFrame frame = focusManager.getLastFocusedFrame();
      if (frame != null) {
        focusOwner = focusManager.getLastFocusedFor(frame);
      }
    }
    return focusOwner;
  }

  @Nullable
  Editor getFocusedEditor() {
    Component component = getFocusedComponent();
    Editor editor = component instanceof EditorComponentImpl ? ((EditorComponentImpl)component).getEditor() : getEditor();
    return editor != null && !editor.isDisposed() ? editor : null;
  }

  @Nullable
  protected VirtualFile getSelectedFile() {
    final Editor editor = getEditor();
    if (editor == null) return null;
    Document document = editor.getDocument();
    return FileDocumentManager.getInstance().getFile(document);
  }

  @NotNull
  protected final Project getProject() {
    return myProject;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    assert statusBar.getProject() == null ||
           statusBar.getProject().equals(myProject) : "Cannot install widget from one project on status bar of another project";

    myStatusBar = statusBar;
    Disposer.register(myStatusBar, this);
    myConnection = myProject.getMessageBus().connect(this);
    myConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
  }

  @Override
  public void dispose() {
    myDisposed = true;
    myStatusBar = null;
    myConnection = null;
  }

  protected final boolean isDisposed() {
    return myDisposed;
  }
}
