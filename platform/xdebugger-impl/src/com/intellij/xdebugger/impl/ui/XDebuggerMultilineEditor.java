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
package com.intellij.xdebugger.impl.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.ui.EditorTextField;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author nik
 */
public class XDebuggerMultilineEditor extends XDebuggerEditorBase {
  private final EditorTextField myEditorTextField;

  public XDebuggerMultilineEditor(Project project,
                                   XDebuggerEditorsProvider debuggerEditorsProvider,
                                   @Nullable @NonNls String historyId,
                                   @Nullable XSourcePosition sourcePosition, @NotNull String text) {
    super(project, debuggerEditorsProvider, historyId, sourcePosition);
    myEditorTextField = new EditorTextField(createDocument(text), project, debuggerEditorsProvider.getFileType()) {
      @Override
      protected EditorEx createEditor() {
        final EditorEx editor = super.createEditor();
        editor.setVerticalScrollbarVisible(true);
        return editor;
      }

      @Override
      protected boolean isOneLineMode() {
        return false;
      }
    };
  }

  public JComponent getComponent() {
    return myEditorTextField;
  }

  protected void doSetText(String text) {
    myEditorTextField.setText(text);
  }

  public String getText() {
    return myEditorTextField.getText();
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    final Editor editor = myEditorTextField.getEditor();
    return editor != null ? editor.getContentComponent() : null;
  }

  public void selectAll() {
    myEditorTextField.selectAll();
  }
}
