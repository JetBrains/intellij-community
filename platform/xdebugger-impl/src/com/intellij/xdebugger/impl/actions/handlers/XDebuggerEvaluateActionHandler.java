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
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler;
import com.intellij.xdebugger.impl.evaluate.XDebuggerEvaluationDialog;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class XDebuggerEvaluateActionHandler extends XDebuggerSuspendedActionHandler {
  protected void perform(@NotNull final XDebugSession session, final DataContext dataContext) {
    XDebuggerEditorsProvider editorsProvider = session.getDebugProcess().getEditorsProvider();
    XStackFrame stackFrame = session.getCurrentStackFrame();
    if (stackFrame == null) return;
    final XDebuggerEvaluator evaluator = stackFrame.getEvaluator();
    if (evaluator == null) return;

    @Nullable Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    @Nullable Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);

    String expression = editor != null ? editor.getSelectionModel().getSelectedText() : null;

    if (expression == null && editor != null) {
      Document document = editor.getDocument();
      TextRange range = evaluator.getExpressionRangeAtOffset(project, document, editor.getCaretModel().getOffset(), true);
      expression = range == null ? null : document.getText(range);
    }

    if (expression == null) {
      XValue value = XDebuggerTreeActionBase.getSelectedValue(dataContext);
      if (value != null) {
        expression = value.getEvaluationExpression();
      }
    }
    if (expression == null) {
      expression = "";
    }
    final XDebuggerEvaluationDialog dialog = new XDebuggerEvaluationDialog(session, editorsProvider, evaluator, expression,
                                                                           stackFrame.getSourcePosition());
    dialog.show();
  }

  protected boolean isEnabled(final @NotNull XDebugSession session, final DataContext dataContext) {
    if (!super.isEnabled(session, dataContext)) {
      return false;
    }

    XStackFrame stackFrame = session.getCurrentStackFrame();
    return stackFrame != null && stackFrame.getEvaluator() != null;
  }
}
