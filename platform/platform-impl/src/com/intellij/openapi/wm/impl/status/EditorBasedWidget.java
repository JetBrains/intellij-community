/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.ui.EditorTextField;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public abstract class EditorBasedWidget implements StatusBarWidget, FileEditorManagerListener {
  public static final String SWING_FOCUS_OWNER_PROPERTY = "focusOwner";

  protected StatusBar myStatusBar;
  protected Project myProject;

  protected MessageBusConnection myConnection;
  private volatile boolean myDisposed;

  protected EditorBasedWidget(@NotNull Project project) {
    myProject = project;
    myConnection = myProject.getMessageBus().connect(this);
    myConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
    Disposer.register(project, this);
  }

  @Nullable
  protected final Editor getEditor() {
    final Project project = getProject();
    if (project == null || project.isDisposed()) return null;

    FileEditor fileEditor = StatusBarUtil.getCurrentFileEditor(project, myStatusBar);
    Editor result = null;
    if (fileEditor instanceof TextEditor) {
      result = ((TextEditor)fileEditor).getEditor();
    }

    if (result == null) {
      final FileEditorManager manager = FileEditorManager.getInstance(project);
      Editor editor = manager.getSelectedTextEditor();
      if (editor != null && WindowManager.getInstance().getStatusBar(editor.getComponent(), project) == myStatusBar) {
        result = editor;
      }
    }

    return result;
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

  Editor getFocusedEditor() {
    Component component = getFocusedComponent();
    return component instanceof EditorComponentImpl ? ((EditorComponentImpl)component).getEditor() : getEditor();
  }

  @Nullable
  protected VirtualFile getSelectedFile() {
    final Editor editor = getEditor();
    if (editor == null) return null;
    Document document = editor.getDocument();
    return FileDocumentManager.getInstance().getFile(document);
  }


  @Nullable
  protected final Project getProject() {
    return myProject;
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    myStatusBar = statusBar;
  }

  @Override
  public void dispose() {
    myDisposed = true;

    myStatusBar = null;
    myConnection.disconnect();
    myConnection = null;
    myProject = null;
  }

  protected final boolean isDisposed() {
    return myDisposed;
  }
}
