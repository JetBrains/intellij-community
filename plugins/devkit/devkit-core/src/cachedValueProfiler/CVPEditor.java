// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.cachedValueProfiler;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.beans.PropertyChangeListener;

public class CVPEditor extends UserDataHolderBase implements FileEditor {
  @NotNull private final VirtualFile myFile;
  private final CVPPanel myPanel;

  public CVPEditor(@NotNull VirtualFile file, @NotNull Project project) {
    myFile = file;
    myPanel = new CVPPanel(file, project);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel;
  }

  @NotNull
  @Override
  public String getName() {
    return myFile.getName();
  }

  @Override
  public void setState(@NotNull FileEditorState state) {}

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  @Override
  public void selectNotify() {}

  @Override
  public void deselectNotify() {}

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {}

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {}

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() { return null;}

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() { return null; }

  @Override
  public void dispose() { }
}
