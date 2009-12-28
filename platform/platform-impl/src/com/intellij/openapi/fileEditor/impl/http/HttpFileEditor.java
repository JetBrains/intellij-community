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
public class HttpFileEditor implements NavigatableFileEditor {
  private final UserDataHolderBase myUserDataHolder = new UserDataHolderBase();
  private final RemoteFilePanel myPanel;

  public HttpFileEditor(final Project project, final HttpVirtualFile virtualFile) {
    myPanel = new RemoteFilePanel(project, virtualFile);
  }

  @NotNull
  public JComponent getComponent() {
    return myPanel.getMainPanel();
  }

  public JComponent getPreferredFocusedComponent() {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      return textEditor.getPreferredFocusedComponent();
    }
    return myPanel.getMainPanel();
  }

  @NotNull
  public String getName() {
    return "Http";
  }

  @NotNull
  public FileEditorState getState(@NotNull final FileEditorStateLevel level) {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      return textEditor.getState(level);
    }
    return new TextEditorState();
  }

  public void setState(@NotNull final FileEditorState state) {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      textEditor.setState(state);
    }
  }

  public boolean isModified() {
    return false;
  }

  public boolean isValid() {
    return true;
  }

  public void selectNotify() {
    myPanel.selectNotify();
  }

  public void deselectNotify() {
    myPanel.deselectNotify();
  }

  public void addPropertyChangeListener(@NotNull final PropertyChangeListener listener) {
    myPanel.addPropertyChangeListener(listener);
  }

  public void removePropertyChangeListener(@NotNull final PropertyChangeListener listener) {
    myPanel.removePropertyChangeListener(listener);
  }

  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      return textEditor.getBackgroundHighlighter();
    }
    return null;
  }

  public boolean canNavigateTo(@NotNull Navigatable navigatable) {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      return textEditor.canNavigateTo(navigatable);
    }
    return false;
  }

  public void navigateTo(@NotNull Navigatable navigatable) {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      textEditor.navigateTo(navigatable);
    }
  }

  public <T> T getUserData(@NotNull Key<T> key) {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      return textEditor.getUserData(key);
    }
    return myUserDataHolder.getUserData(key);
  }

  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      textEditor.putUserData(key, value);
    }
    else {
      myUserDataHolder.putUserData(key, value);
    }
  }

  public FileEditorLocation getCurrentLocation() {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      return textEditor.getCurrentLocation();
    }
    return null;
  }

  public StructureViewBuilder getStructureViewBuilder() {
    final TextEditor textEditor = myPanel.getFileEditor();
    if (textEditor != null) {
      return textEditor.getStructureViewBuilder();
    }
    return null;
  }

  public void dispose() {
    myPanel.dispose();
  }
}
