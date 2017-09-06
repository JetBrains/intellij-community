/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.CompoundBorder;

/**
 * @author nik
 */
public class XDebuggerExpressionEditor extends XDebuggerEditorBase {
  private final JComponent myComponent;
  private final EditorTextField myEditorTextField;
  private XExpression myExpression;

  public XDebuggerExpressionEditor(Project project,
                                   @NotNull XDebuggerEditorsProvider debuggerEditorsProvider,
                                   @Nullable @NonNls String historyId,
                                   @Nullable XSourcePosition sourcePosition,
                                   @NotNull XExpression text,
                                   final boolean multiline,
                                   boolean editorFont,
                                   boolean showEditor) {
    super(project, debuggerEditorsProvider, multiline ? EvaluationMode.CODE_FRAGMENT : EvaluationMode.EXPRESSION, historyId, sourcePosition);
    myExpression = XExpressionImpl.changeMode(text, getMode());
    myEditorTextField =
      new EditorTextField(createDocument(myExpression), project, debuggerEditorsProvider.getFileType(), false, !multiline) {
      @Override
      protected EditorEx createEditor() {
        final EditorEx editor = super.createEditor();
        editor.setHorizontalScrollbarVisible(multiline);
        editor.setVerticalScrollbarVisible(multiline);
        editor.getSettings().setUseSoftWraps(isUseSoftWraps());
        editor.getColorsScheme().setEditorFontName(getFont().getFontName());
        editor.getColorsScheme().setEditorFontSize(getFont().getSize());
        if (multiline) {
          editor.getContentComponent().setBorder(new CompoundBorder(editor.getContentComponent().getBorder(), JBUI.Borders.emptyLeft(2)));
        }
        else if (showEditor) {
          setExpandable(editor);
        }
        return editor;
      }

      @Override
      public Object getData(String dataId) {
        if (LangDataKeys.CONTEXT_LANGUAGES.is(dataId)) {
          return new Language[]{myExpression.getLanguage()};
        } else if (CommonDataKeys.PSI_FILE.is(dataId)) {
          return PsiDocumentManager.getInstance(getProject()).getPsiFile(getDocument());
        }
        return super.getData(dataId);
      }
    };
    if (editorFont) {
      myEditorTextField.setFontInheritedFromLAF(false);
      myEditorTextField.setFont(EditorUtil.getEditorFont());
    }
    myComponent = decorate(myEditorTextField, multiline, showEditor);
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public JComponent getEditorComponent() {
    return myEditorTextField;
  }

  @Override
  protected void doSetText(XExpression text) {
    myExpression = text;
    myEditorTextField.setNewDocumentAndFileType(getFileType(text), createDocument(text));
  }

  @Override
  public XExpression getExpression() {
    return getEditorsProvider().createExpression(getProject(), myEditorTextField.getDocument(), myExpression.getLanguage(), myExpression.getMode());
  }

  @Override
  @Nullable
  public JComponent getPreferredFocusedComponent() {
    final Editor editor = myEditorTextField.getEditor();
    return editor != null ? editor.getContentComponent() : null;
  }

  public void setEnabled(boolean enable) {
    if (enable == myComponent.isEnabled()) return;
    UIUtil.setEnabled(myComponent, enable, true);
  }

  @Nullable
  @Override
  public Editor getEditor() {
    return myEditorTextField.getEditor();
  }

  @Override
  public void selectAll() {
    myEditorTextField.selectAll();
  }
}
