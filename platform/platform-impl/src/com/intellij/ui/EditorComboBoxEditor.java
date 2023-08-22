// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionListener;

/**
 * Combobox items are Documents for this combobox
 * @author max
 */
public class EditorComboBoxEditor implements ComboBoxEditor{
  private final EditorTextField myTextField;
  protected static final @NonNls String NAME = "ComboBox.textField";

  public EditorComboBoxEditor(Project project, FileType fileType) {
    myTextField = new ComboboxEditorTextField((Document)null, project, fileType) {
      @Override
      protected @NotNull EditorEx createEditor() {
        EditorEx editor = super.createEditor();
        onEditorCreate(editor);
        return editor;
      }
    };
    myTextField.setName(NAME);
  }

  protected void onEditorCreate(EditorEx editor) {}

  @Override
  public void selectAll() {
    myTextField.selectAll();
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myTextField, true));
  }

  public @Nullable Editor getEditor() {
    return myTextField.getEditor();
  }

  @Override
  public EditorTextField getEditorComponent() {
    return myTextField;
  }

  @Override
  public void addActionListener(ActionListener l) {

  }

  @Override
  public void removeActionListener(ActionListener l) {

  }

  @Override
  public Object getItem() {
    return getDocument();
  }

  protected Document getDocument() {
    return myTextField.getDocument();
  }

  @Override
  public void setItem(Object anObject) {
    myTextField.setDocument((Document)anObject);
  }
}
