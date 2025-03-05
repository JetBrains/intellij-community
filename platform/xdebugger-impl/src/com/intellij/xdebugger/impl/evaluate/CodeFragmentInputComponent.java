// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate;

import com.intellij.codeInsight.inline.completion.InlineCompletion;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBSplitter;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.ui.XDebuggerEditorBase;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class CodeFragmentInputComponent extends EvaluationInputComponent {
  private final XDebuggerExpressionEditor myMultilineEditor;
  private final ExpressionInputForm myMainForm = new ExpressionInputForm();
  private final String mySplitterProportionKey;

  public CodeFragmentInputComponent(final @NotNull Project project,
                                    @NotNull XDebuggerEditorsProvider editorsProvider,
                                    final @Nullable XSourcePosition sourcePosition,
                                    @Nullable XExpression statements,
                                    String splitterProportionKey,
                                    Disposable parentDisposable) {
    super(XDebuggerBundle.message("dialog.title.evaluate.code.fragment"));
    myMultilineEditor = new XDebuggerExpressionEditor(project, editorsProvider, "evaluateCodeFragment", sourcePosition,
                                                      statements != null ? statements : XExpressionImpl.EMPTY_CODE_FRAGMENT, true, true, false) {
      @Override
      protected void prepareEditor(EditorEx editor) {
        super.prepareEditor(editor);
        XDebugSessionImpl session = (XDebugSessionImpl)XDebuggerManager.getInstance(project).getCurrentSession();
        if (session != null) {
          InlineCompletion.INSTANCE.install(editor, session.getCoroutineScope());
        }
      }
    };

    myMainForm.setName(XDebuggerBundle.message("xdebugger.label.text.code.fragment"));
    myMainForm.addExpressionComponent(myMultilineEditor.getComponent());
    myMainForm.addLanguageComponent(myMultilineEditor.getLanguageChooser());

    mySplitterProportionKey = splitterProportionKey;
  }

  @Override
  public @NotNull XDebuggerEditorBase getInputEditor() {
    return myMultilineEditor;
  }

  @Override
  public JPanel getMainComponent() {
    return myMainForm.getMainPanel();
  }

  @Override
  public void addComponent(JPanel contentPanel, JPanel resultPanel) {
    final JBSplitter splitter = new JBSplitter(true, 0.3f, 0.2f, 0.7f);
    splitter.setSplitterProportionKey(mySplitterProportionKey);
    contentPanel.add(splitter, BorderLayout.CENTER);
    splitter.setFirstComponent(myMainForm.getMainPanel());
    splitter.setSecondComponent(resultPanel);
  }
}
