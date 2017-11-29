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
package com.intellij.xdebugger.impl.evaluate;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.JBSplitter;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.ui.XDebuggerEditorBase;
import com.intellij.xdebugger.impl.ui.XDebuggerExpressionEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
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
                                                      statements != null ? statements : XExpressionImpl.EMPTY_CODE_FRAGMENT, true, true, false);

    myMainForm.setName(XDebuggerBundle.message("xdebugger.label.text.code.fragment"));
    myMainForm.addExpressionComponent(myMultilineEditor.getComponent());
    myMainForm.addLanguageComponent(myMultilineEditor.getLanguageChooser());

    mySplitterProportionKey = splitterProportionKey;
  }

  @Override
  @NotNull
  public XDebuggerEditorBase getInputEditor() {
    return myMultilineEditor;
  }

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
