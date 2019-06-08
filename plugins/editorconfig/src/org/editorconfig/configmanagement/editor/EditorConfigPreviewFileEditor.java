// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.editor;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.util.Key;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.util.ObjectUtils;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;

public class EditorConfigPreviewFileEditor implements FileEditor {
  private final Editor myEditor;

  public EditorConfigPreviewFileEditor(Editor editor) {
    myEditor = editor;
    if (myEditor instanceof EditorEx) {
      ((EditorEx)myEditor).setPermanentHeaderComponent(getHeaderComponent());
    }
    myEditor.setHeaderComponent(getHeaderComponent());
  }

  private static JComponent getHeaderComponent() {
    JPanel previewHeaderPanel = new JPanel();
    previewHeaderPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
    JLabel warningLabel = new JLabel(EditorConfigBundle.message("editor.preview.not.saved.warning"));
    warningLabel.setForeground(JBColor.GRAY);
    previewHeaderPanel.add(warningLabel);
    previewHeaderPanel.setBorder(IdeBorderFactory.createBorder());
    return previewHeaderPanel;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myEditor.getComponent();
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return null;
  }

  @NotNull
  @Override
  public String getName() {
    return "Preview";
  }

  @Override
  public void setState(@NotNull FileEditorState state) {

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

  }

  @Override
  public void deselectNotify() {

  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public void dispose() {
    EditorFactory.getInstance().releaseEditor(myEditor);
  }

  @Nullable
  @Override
  public <T> T getUserData(@NotNull Key<T> key) {
    return null;
  }

  @Override
  public <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {

  }
}
