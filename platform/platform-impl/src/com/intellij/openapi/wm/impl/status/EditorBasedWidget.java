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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class EditorBasedWidget extends FileEditorManagerAdapter implements StatusBarWidget {

  protected StatusBar myStatusBar;
  protected Project myProject;

  protected MessageBusConnection myConnection;

  protected EditorBasedWidget(Project project) {
    myProject = project;
    myConnection = myProject.getMessageBus().connect();
    myConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this);
  }

  protected EditorBasedWidget() {
  }

  @Nullable
  protected final Editor getEditor() {
    final Project project = getProject();
    if (project != null) {
      final FileEditorManager manager = FileEditorManager.getInstance(project);
      Editor editor = manager.getSelectedTextEditor();
      if (editor != null && WindowManager.getInstance().getStatusBar(editor.getComponent()) == myStatusBar) {
        return editor;
      }
    }

    return null;
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


  @NotNull
  protected final Project getProject() {
    if (myProject != null) return myProject;

    if (myStatusBar != null && myStatusBar.getFrame() != null) {
      return myStatusBar.getFrame().getProject();
    } else {
      assert false : "Cannot find project (unititialized status bar widget?)";
      return null;
    }
  }

  @Override
  public void install(@NotNull StatusBar statusBar) {
    myStatusBar = statusBar;
  }

  @Override
  public void dispose() {
    myStatusBar = null;
    if (myConnection != null) {
      myConnection.disconnect();
      myConnection = null;
    }
    myProject = null;
  }
}
