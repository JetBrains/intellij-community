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
package com.intellij.xdebugger.impl.evaluate;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
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
  private final JPanel myMainPanel;
  private final String mySplitterProportionKey;

  public CodeFragmentInputComponent(final @NotNull Project project,
                                    @NotNull XDebuggerEditorsProvider editorsProvider,
                                    final @Nullable XSourcePosition sourcePosition,
                                    @Nullable XExpression statements,
                                    String splitterProportionKey,
                                    Disposable parentDisposable) {
    super(XDebuggerBundle.message("dialog.title.evaluate.code.fragment"));
    myMultilineEditor = new XDebuggerExpressionEditor(project, editorsProvider, "evaluateCodeFragment", sourcePosition,
                                                      statements != null ? statements : XExpressionImpl.EMPTY_CODE_FRAGMENT, true, true);
    myMainPanel = new JPanel(new BorderLayout());
    JPanel editorPanel = new JPanel(new BorderLayout());
    editorPanel.add(myMultilineEditor.getComponent(), BorderLayout.CENTER);
    DefaultActionGroup group = new DefaultActionGroup();
    group.add(new HistoryNavigationAction(false, IdeActions.ACTION_PREVIOUS_OCCURENCE, parentDisposable));
    group.add(new HistoryNavigationAction(true, IdeActions.ACTION_NEXT_OCCURENCE, parentDisposable));
    editorPanel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, group, false).getComponent(), BorderLayout.EAST);
    //myMainPanel.add(new JLabel(XDebuggerBundle.message("xdebugger.label.text.code.fragment")), BorderLayout.NORTH);
    myMainPanel.add(editorPanel, BorderLayout.CENTER);
    if (statements != null) {
      myMultilineEditor.setExpression(statements);
    }
    mySplitterProportionKey = splitterProportionKey;
  }

  @Override
  @NotNull
  protected XDebuggerEditorBase getInputEditor() {
    return myMultilineEditor;
  }

  @Override
  public void addComponent(JPanel contentPanel, JPanel resultPanel) {
    final JBSplitter splitter = new JBSplitter(true, 0.3f, 0.2f, 0.7f);
    splitter.setSplitterProportionKey(mySplitterProportionKey);
    contentPanel.add(splitter, BorderLayout.CENTER);
    splitter.setFirstComponent(myMainPanel);
    splitter.setSecondComponent(resultPanel);
  }

  private class HistoryNavigationAction extends AnAction {
    private final boolean myForward;

    public HistoryNavigationAction(boolean forward, String actionId, Disposable parentDisposable) {
      myForward = forward;
      final AnAction action = ActionManager.getInstance().getAction(actionId);
      copyFrom(action);
      registerCustomShortcutSet(action.getShortcutSet(), myMainPanel, parentDisposable);
    }

    @Override
    public void update(AnActionEvent e) {
      e.getPresentation().setEnabled(myForward ? myMultilineEditor.canGoForward() : myMultilineEditor.canGoBackward());
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (myForward) {
        myMultilineEditor.goForward();
      }
      else {
        myMultilineEditor.goBackward();
      }
    }
  }
}
