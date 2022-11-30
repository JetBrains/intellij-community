// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.evaluate.quick;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
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
import org.jetbrains.concurrency.Promises;

import java.awt.*;

public class XQuickEvaluateHandler extends QuickEvaluateHandler {
  private static final Logger LOG = Logger.getInstance(XQuickEvaluateHandler.class);

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
  public CancellableHint createValueHintAsync(@NotNull final Project project, @NotNull final Editor editor, @NotNull final Point point, final ValueHintType type) {
    final XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
    if (session == null) {
      return CancellableHint.resolved(null);
    }

    final XDebuggerEvaluator evaluator = session.getDebugProcess().getEvaluator();
    if (evaluator == null) {
      return CancellableHint.resolved(null);
    }
    int offset = AbstractValueHint.calculateOffset(editor, point);
    Promise<ExpressionInfo> infoPromise = getExpressionInfo(evaluator, project, type, editor, offset);
    Promise<AbstractValueHint> hintPromise = infoPromise
      .thenAsync(expressionInfo -> {
        AsyncPromise<AbstractValueHint> resultPromise = new AsyncPromise<>();
        UIUtil.invokeLaterIfNeeded(() -> {
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
          resultPromise.setResult(new XValueHint(project, editor, point, type, expressionInfo, evaluator, session, false));
        });
        return resultPromise;
      });
    return new CancellableHint(hintPromise, infoPromise);
  }


  @NotNull
  private static Promise<ExpressionInfo> getExpressionInfo(final XDebuggerEvaluator evaluator, final Project project,
                                                           final ValueHintType type, @NotNull Editor editor, final int offset) {
    SelectionModel selectionModel = editor.getSelectionModel();
    int selectionStart = selectionModel.getSelectionStart();
    int selectionEnd = selectionModel.getSelectionEnd();
    if ((type == ValueHintType.MOUSE_CLICK_HINT || type == ValueHintType.MOUSE_ALT_OVER_HINT) && selectionModel.hasSelection()
        && selectionStart <= offset && offset <= selectionEnd) {
      return Promises.resolvedPromise(new ExpressionInfo(new TextRange(selectionStart, selectionEnd)));
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
