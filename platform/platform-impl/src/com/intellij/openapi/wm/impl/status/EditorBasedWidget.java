// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.EditorTextField;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public abstract class EditorBasedWidget implements StatusBarWidget, FileEditorManagerListener {

  protected final @NotNull Project myProject;

  protected StatusBar myStatusBar;
  protected MessageBusConnection myConnection;
  private volatile boolean myDisposed;

  protected EditorBasedWidget(@NotNull Project project) {
    myProject = project;
    Disposer.register(project, this);
  }

  protected @Nullable Editor getEditor() {
    Editor editor = StatusBarUtil.getCurrentTextEditor(myStatusBar);
    if (editor != null) {
      return editor;
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    }
    FileEditor fileEditor = StatusBarUtil.getCurrentFileEditor(myStatusBar);
    return fileEditor instanceof TextEditor ? ((TextEditor)fileEditor).getEditor() : null;
  }

  public boolean isOurEditor(Editor editor) {
    return editor != null &&
           editor.getComponent().isShowing() &&
           !Boolean.TRUE.equals(editor.getUserData(EditorTextField.SUPPLEMENTARY_KEY)) &&
           WindowManager.getInstance().getStatusBar(editor.getComponent(), editor.getProject()) == myStatusBar;
  }

  @Nullable Component getFocusedComponent() {
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focusOwner == null) {
      IdeFocusManager focusManager = IdeFocusManager.getInstance(myProject);
      Window frame = focusManager.getLastFocusedIdeWindow();
      if (frame != null) {
        focusOwner = focusManager.getLastFocusedFor(frame);
      }
    }
    return focusOwner;
  }

  @Nullable Editor getFocusedEditor() {
    Component component = getFocusedComponent();
    Editor editor = component instanceof EditorComponentImpl ? ((EditorComponentImpl)component).getEditor() : getEditor();
    return editor != null && !editor.isDisposed() ? editor : null;
  }

  protected @Nullable VirtualFile getSelectedFile() {
    Editor editor = getEditor();
    if (editor == null) {
      return null;
    }
    Document document = editor.getDocument();
    return FileDocumentManager.getInstance().getFile(document);
  }

  protected final @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    assert statusBar.getProject() == null ||
           statusBar.getProject().equals(myProject) : "Cannot install widget from one project on status bar of another project";

    myStatusBar = statusBar;
    Disposer.register(myStatusBar, this);

    if (myProject.isDisposed()) {
      return;
    }

    myConnection = myProject.getMessageBus().connect(this);
    myConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
  }

  @Override
  public void dispose() {
    myDisposed = true;
    myStatusBar = null;
  }

  protected final boolean isDisposed() {
    return myDisposed;
  }
}
