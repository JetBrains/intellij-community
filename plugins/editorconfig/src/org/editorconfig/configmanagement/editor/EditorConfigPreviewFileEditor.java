// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.editor;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsChangeEvent;
import com.intellij.psi.codeStyle.CodeStyleSettingsListener;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import org.editorconfig.language.messages.EditorConfigBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;

public class EditorConfigPreviewFileEditor implements FileEditor, CodeStyleSettingsListener {
  private final Editor                  myEditor;
  private final EditorConfigPreviewFile myPreviewFile;

  public EditorConfigPreviewFileEditor(@NotNull Editor editor, @NotNull EditorConfigPreviewFile previewFile) {
    myEditor = editor;
    myPreviewFile = previewFile;
    if (myEditor instanceof EditorEx) {
      ((EditorEx)myEditor).setPermanentHeaderComponent(getHeaderComponent());
    }
    myEditor.setHeaderComponent(getHeaderComponent());
    final EditorSettings editorSettings = myEditor.getSettings();
    editorSettings.setWhitespacesShown(true);
    editorSettings.setGutterIconsShown(false);
    editorSettings.setLineNumbersShown(false);
    updateEditor();
    CodeStyleSettingsManager.getInstance(myEditor.getProject()).addListener(this);
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
    return EditorConfigBundle.message("preview.file.editor.name");
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
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {

  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myPreviewFile;
  }

  @Override
  public void dispose() {
    CodeStyleSettingsManager.removeListener(myEditor.getProject(), this);
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

  @Override
  public void codeStyleSettingsChanged(@NotNull CodeStyleSettingsChangeEvent event) {
    updateEditor();
  }

  private void updateEditor() {
    PsiFile originalPsi = myPreviewFile.resolveOriginalPsi();
    if (originalPsi != null && !myEditor.isDisposed()) {
      CodeStyleSettings settings = CodeStyle.getSettings(originalPsi);
      final Language language = myPreviewFile.getLanguage();
      myEditor.getSettings().setRightMargin(settings.getRightMargin(language));
      myEditor.getSettings().setSoftMargins(settings.getSoftMargins(language));
      myEditor.getSettings().setTabSize(settings.getTabSize(myPreviewFile.getFileType()));
    }
  }

}
