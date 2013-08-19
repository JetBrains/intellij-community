/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor.impl.http;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.TextEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;

/**
 * @author nik
 */
public class HttpFileEditor implements TextEditor {
  private final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();
  private final RemoteFilePanel myPanel;
  private Editor myMockTextEditor;
  private final Project myProject;

  public HttpFileEditor(final Project project, final HttpVirtualFile virtualFile) {
    myProject = project;
    myPanel = new RemoteFilePanel(project, virtualFile);
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return myPanel.getMainPanel();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      return textEditor.getPreferredFocusedComponent();
    }
    return myPanel.getMainPanel();
  }

  @Override
  @NotNull
  public String getName() {
    return "Http";
  }

  @Override
  @NotNull
  public Editor getEditor() {
    final TextEditor fileEditor = myPanel.getFileEditor();
    if (fileEditor != null) {
      return fileEditor.getEditor();
    }
    if (myMockTextEditor == null) {
      myMockTextEditor = EditorFactory.getInstance().createViewer(new DocumentImpl(""), myProject);
    }
    return myMockTextEditor;
  }

  @Override
  @NotNull
  public FileEditorState getState(@NotNull final FileEditorStateLevel level) {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      return textEditor.getState(level);
    }
    return new TextEditorState();
  }

  @Override
  public void setState(@NotNull final FileEditorState state) {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      textEditor.setState(state);
    }
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public void selectNotify() {
    myPanel.selectNotify();
  }

  @Override
  public void deselectNotify() {
    myPanel.deselectNotify();
  }

  @Override
  public void addPropertyChangeListener(@NotNull final PropertyChangeListener listener) {
    myPanel.addPropertyChangeListener(listener);
  }

  @Override
  public void removePropertyChangeListener(@NotNull final PropertyChangeListener listener) {
    myPanel.removePropertyChangeListener(listener);
  }

  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      return textEditor.getBackgroundHighlighter();
    }
    return null;
  }

  @Override
  public boolean canNavigateTo(@NotNull Navigatable navigatable) {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      return textEditor.canNavigateTo(navigatable);
    }
    return false;
  }

  @Override
  public void navigateTo(@NotNull Navigatable navigatable) {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      textEditor.navigateTo(navigatable);
    }
  }

  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      return textEditor.getUserData(key);
    }
    return myUserDataHolder.getUserData(key);
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      textEditor.putUserData(key, value);
    }
    else {
      myUserDataHolder.putUserData(key, value);
    }
  }

  @Override
  public FileEditorLocation getCurrentLocation() {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      return textEditor.getCurrentLocation();
    }
    return null;
  }

  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      return textEditor.getStructureViewBuilder();
    }
    return null;
  }

  @Override
  public void dispose() {
    if (myMockTextEditor != null) {
      EditorFactory.getInstance().releaseEditor(myMockTextEditor);
    }
    myPanel.dispose();
  }
}
