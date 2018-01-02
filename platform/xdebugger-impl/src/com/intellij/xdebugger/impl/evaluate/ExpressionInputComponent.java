// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.evaluate;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.ui.XDebuggerEditorBase;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionComboBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class ExpressionInputComponent extends EvaluationInputComponent {
  private final XDebuggerEditorBase myExpressionEditor;
  private final ExpressionInputForm myMainForm = new ExpressionInputForm();

  public ExpressionInputComponent(final @NotNull Project project,
                                  @NotNull XDebuggerEditorsProvider editorsProvider,
                                  @Nullable String historyId,
                                  final @Nullable XSourcePosition sourcePosition,
                                  @Nullable XExpression expression,
                                  @NotNull Disposable parentDisposable,
                                  boolean showHelp) {
    super(XDebuggerBundle.message("xdebugger.dialog.title.evaluate.expression"));
    BorderLayoutPanel expressionPanel = JBUI.Panels.simplePanel();
    myExpressionEditor = new XDebuggerExpressionComboBox(project, editorsProvider, historyId, sourcePosition, true, false) {
      @Override
      protected void prepareEditor(Editor editor) {
        Font font = EditorUtil.getEditorFont();
        editor.getColorsScheme().setEditorFontName(font.getFontName());
        editor.getColorsScheme().setEditorFontSize(font.getSize());
      }
    };
    myExpressionEditor.setExpression(expression);
    expressionPanel.addToCenter(myExpressionEditor.getComponent());
    final JBLabel help = new JBLabel(XDebuggerBundle.message("xdebugger.evaluate.addtowatches.hint",
                                                             KeymapUtil.getKeystrokeText(XDebuggerEvaluationDialog.ADD_WATCH_KEYSTROKE)),
                                     SwingConstants.RIGHT);
    help.setBorder(JBUI.Borders.empty(2, 0, 6, 0));
    help.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    help.setFontColor(UIUtil.FontColor.BRIGHTER);
    expressionPanel.addToBottom(help);
    help.setVisible(showHelp);

    myMainForm.addExpressionComponent(expressionPanel);
    myMainForm.addLanguageComponent(myExpressionEditor.getLanguageChooser());
  }

  @Override
  public void addComponent(JPanel contentPanel, JPanel resultPanel) {
    contentPanel.add(resultPanel, BorderLayout.CENTER);
    contentPanel.add(myMainForm.getMainPanel(), BorderLayout.NORTH);
  }

  public JPanel getMainComponent() {
    return myMainForm.getMainPanel();
  }

  @NotNull
  public XDebuggerEditorBase getInputEditor() {
    return myExpressionEditor;
  }
}
