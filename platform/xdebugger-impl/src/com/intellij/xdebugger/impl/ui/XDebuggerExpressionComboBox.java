/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.EditorComboBoxEditor;
import com.intellij.ui.EditorComboBoxRenderer;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.XDebuggerHistoryManager;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class XDebuggerExpressionComboBox extends XDebuggerEditorBase {
  private final JComponent myComponent;
  private final ComboBox myComboBox;
  private EditorComboBoxEditor myEditor;
  private XExpression myExpression;

  public XDebuggerExpressionComboBox(final @NotNull Project project, final @NotNull XDebuggerEditorsProvider debuggerEditorsProvider, final @Nullable @NonNls String historyId,
                                     final @Nullable XSourcePosition sourcePosition) {
    super(project, debuggerEditorsProvider, EvaluationMode.EXPRESSION, historyId, sourcePosition);
    myComboBox = new ComboBox(100);
    myComboBox.setEditable(true);
    myExpression = XExpressionImpl.EMPTY_EXPRESSION;
    Dimension minimumSize = new Dimension(myComboBox.getMinimumSize());
    minimumSize.width = 100;
    myComboBox.setMinimumSize(minimumSize);
    initEditor();
    fillComboBox();
    myComponent = addChooseFactoryLabel(myComboBox, false);
  }

  public ComboBox getComboBox() {
    return myComboBox;
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Nullable
  public Editor getEditor() {
    return myEditor.getEditor();
  }

  public JComponent getEditorComponent() {
    return myEditor.getEditorComponent();
  }

  public void setEnabled(boolean enable) {
    if (enable == myComboBox.isEnabled()) return;

    UIUtil.setEnabled(myComponent, enable, true);
    //myComboBox.setEditable(enable);

    if (enable) {
      //initEditor();
    }
    else {
      myExpression = getExpression();
    }
  }

  private void initEditor() {
    myEditor = new EditorComboBoxEditor(getProject(), getEditorsProvider().getFileType()) {
      @Override
      public void setItem(Object anObject) {
        if (anObject == null) {
          anObject = XExpressionImpl.EMPTY_EXPRESSION;
        }
        XExpression expression = (XExpression)anObject;
        getEditorComponent().setNewDocumentAndFileType(getFileType(expression), createDocument(expression));
      }

      @Override
      protected void onEditorCreate(EditorEx editor) {
        editor.putUserData(DebuggerCopyPastePreprocessor.REMOVE_NEWLINES_ON_PASTE, true);
        editor.getColorsScheme().setEditorFontSize(myComboBox.getFont().getSize());
      }
    };
    myEditor.getEditorComponent().setFontInheritedFromLAF(false);
    myComboBox.setEditor(myEditor);
    //myEditor.setItem(myExpression);
    myComboBox.setRenderer(new EditorComboBoxRenderer(myEditor));
    myComboBox.setMaximumRowCount(XDebuggerHistoryManager.MAX_RECENT_EXPRESSIONS);
  }

  @Override
  protected void onHistoryChanged() {
    fillComboBox();
  }

  private void fillComboBox() {
    myComboBox.removeAllItems();
    for (XExpression expression : getRecentExpressions()) {
      myComboBox.addItem(expression);
    }
    if (myComboBox.getItemCount() > 0) {
      myComboBox.setSelectedIndex(0);
    }
  }

  @Override
  protected void doSetText(XExpression text) {
    if (myComboBox.getItemCount() > 0) {
      myComboBox.setSelectedIndex(0);
    }

    //if (myComboBox.isEditable()) {
      myEditor.setItem(text);
    //}
    myExpression = text;
  }

  @Override
  public XExpression getExpression() {
    Object document = myEditor.getItem();
    if (document instanceof Document) { // sometimes null on Mac
      return getEditorsProvider().createExpression(getProject(), (Document)document, myExpression.getLanguage(), EvaluationMode.EXPRESSION);
    }
    return myExpression;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return (JComponent)myComboBox.getEditor().getEditorComponent();
  }

  @Override
  public void selectAll() {
    myComboBox.getEditor().selectAll();
  }
}
