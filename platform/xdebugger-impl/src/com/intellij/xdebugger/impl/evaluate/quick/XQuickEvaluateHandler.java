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
package com.intellij.xdebugger.impl.evaluate.quick;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.impl.evaluate.quick.common.AbstractValueHint;
import com.intellij.xdebugger.impl.evaluate.quick.common.QuickEvaluateHandler;
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.awt.*;

/**
 * @author nik
 */
public class XQuickEvaluateHandler extends QuickEvaluateHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.xdebugger.impl.evaluate.quick.XQuickEvaluateHandler");

  @Override
  public boolean isEnabled(@NotNull final Project project) {
    XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    return session != null && session.getDebugProcess().getEvaluator() != null;
  }

  @Nullable
  @Override
  public AbstractValueHint createValueHint(@NotNull Project project, @NotNull Editor editor, @NotNull Point point, ValueHintType type) {
    return null;
  }

  @NotNull
  @Override
  public Promise<AbstractValueHint> createValueHintAsync(@NotNull final Project project, @NotNull final Editor editor, @NotNull final Point point, final ValueHintType type) {
    final XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session == null) {
      return Promise.resolve(null);
    }

    final XDebuggerEvaluator evaluator = session.getDebugProcess().getEvaluator();
    if (evaluator == null) {
      return Promise.resolve(null);
    }
    int offset = AbstractValueHint.calculateOffset(editor, point);
    Promise<ExpressionInfo> expressionInfoPromise = getExpressionInfo(evaluator, project, type, editor, offset);

    return expressionInfoPromise.thenAsync(expressionInfo -> {
      AsyncPromise<AbstractValueHint> resultPromise = new AsyncPromise<>();
      UIUtil.invokeLaterIfNeeded(() -> PsiDocumentManager.getInstance(project).performWhenAllCommitted(() -> {
        int textLength = editor.getDocument().getTextLength();
        if (expressionInfo == null) {
          resultPromise.setResult(null);
          return;
        }
        TextRange range = expressionInfo.getTextRange();
        if (range.getStartOffset() > range.getEndOffset() || range.getStartOffset() < 0 || range.getEndOffset() > textLength) {
          LOG.error("invalid range: " + range + ", text length = " + textLength + ", evaluator: " + evaluator);
          resultPromise.setResult(null);
          return;
        }
        resultPromise.setResult(new XValueHint(project, editor, point, type, expressionInfo, evaluator, session));
      }));
      return resultPromise;
    });
  }


  @NotNull
  private static Promise<ExpressionInfo> getExpressionInfo(final XDebuggerEvaluator evaluator, final Project project,
                                                                     final ValueHintType type,
                                                                     final Editor editor, final int offset) {
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getSelectionStart();
    int selectionEnd = selectionModel.getSelectionEnd();
    if ((type == ValueHintType.MOUSE_CLICK_HINT || type == ValueHintType.MOUSE_ALT_OVER_HINT) && selectionModel.hasSelection()
        && selectionStart <= offset && offset <= selectionEnd) {
      return Promise.resolve(new ExpressionInfo(new TextRange(selectionStart, selectionEnd)));
    }
    return evaluator.getExpressionInfoAtOffsetAsync (project, editor.getDocument(), offset, type == ValueHintType.MOUSE_CLICK_HINT || type == ValueHintType.MOUSE_ALT_OVER_HINT);
  }

  @Override
  public boolean canShowHint(@NotNull final Project project) {
    return isEnabled(project);
  }

  @Override
  public int getValueLookupDelay(Project project) {
    return XDebuggerSettingsManager.getInstance().getDataViewSettings().getValueLookupDelay();
  }
}
