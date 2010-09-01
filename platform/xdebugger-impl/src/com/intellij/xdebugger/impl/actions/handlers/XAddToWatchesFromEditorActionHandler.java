/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class XAddToWatchesFromEditorActionHandler extends XDebuggerActionHandler {
  @Override
  protected boolean isEnabled(@NotNull XDebugSession session, DataContext dataContext) {
    return getTextToEvaluate(dataContext, session) != null;
  }

  @Nullable
  private static String getTextToEvaluate(DataContext dataContext, XDebugSession session) {
    final Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return null;
    }

    String text = editor.getSelectionModel().getSelectedText();
    if (text == null && session.isSuspended()) {
      final XStackFrame stackFrame = session.getCurrentStackFrame();
      if (stackFrame != null) {
        final XDebuggerEvaluator evaluator = stackFrame.getEvaluator();
        if (evaluator != null) {
          final int offset = editor.getCaretModel().getOffset();
          final Document document = editor.getDocument();
          final TextRange textRange = evaluator.getExpressionRangeAtOffset(session.getProject(), document, offset);
          if (textRange != null) {
            text = document.getText(textRange);
          }
        }
      }
    }

    return StringUtil.isEmptyOrSpaces(text) ? null : text;
  }

  @Override
  protected void perform(@NotNull XDebugSession session, DataContext dataContext) {
    final String text = getTextToEvaluate(dataContext, session);
    if (text == null) return;

    ((XDebugSessionImpl)session).getSessionTab().getWatchesView().addWatchExpression(text, -1);
  }
}
