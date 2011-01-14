/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.DockableEditorTabbedContainer;
import com.intellij.openapi.fileEditor.impl.EditorWithProviderComposite;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.docking.DockContainer;
import com.intellij.ui.docking.DockManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class EditorBasedWidget extends FileEditorManagerAdapter implements StatusBarWidget {

  protected StatusBar myStatusBar;
  private Project myProject;

  protected MessageBusConnection myConnection;
  private boolean myDisposed;

  protected EditorBasedWidget(Project project) {
    myProject = project;
    myConnection = myProject.getMessageBus().connect();
    myConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
  }

  @Nullable
  protected final Editor getEditor() {
    final Project project = getProject();

    if (project == null) return null;

    DockContainer c = DockManager.getInstance(project).getContainerFor(myStatusBar.getComponent());
    EditorsSplitters splitters = null;
    if (c instanceof DockableEditorTabbedContainer) {
      splitters = ((DockableEditorTabbedContainer)c).getSplitters();
    }

    Editor result = null;
    if (splitters != null && splitters.getCurrentWindow() != null) {
      EditorWithProviderComposite editor = splitters.getCurrentWindow().getSelectedEditor();
      if (editor != null) {
        FileEditor fileEditor = editor.getSelectedEditorWithProvider().getFirst();
        if (fileEditor instanceof TextEditor) {
          result = ((TextEditor)fileEditor).getEditor();
        }
      }
    }

    if (result == null) {
      final FileEditorManager manager = FileEditorManager.getInstance(project);
      Editor editor = manager.getSelectedTextEditor();
      if (editor != null && WindowManager.getInstance().getStatusBar(editor.getComponent()) == myStatusBar) {
        result = editor;
      }
    }

    return result;
  }


  protected boolean isOurEditor(Editor editor) {
    if (editor == null) return false;
    return getEditor() == editor;
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
    if (myConnection != null) {
      myConnection.disconnect();
      myConnection = null;
    }
    myProject = null;
  }

  protected final boolean isDisposed() {
    return myDisposed;
  }
}
