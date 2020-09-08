// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.inline.XInlineWatchesViewImpl;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class XAddToInlineWatchesFromEditorActionHandler extends XDebuggerActionHandler {
  @Override
  protected boolean isEnabled(@NotNull XDebugSession session, DataContext dataContext) {
    return true;
  }

  @NotNull
  protected static Promise<String> getTextToEvaluate(DataContext dataContext, XDebugSession session) {
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      return Promises.resolvedPromise(null);
    }

    String text = editor.getSelectionModel().getSelectedText();
    if (text != null) {
      return Promises.resolvedPromise(StringUtil.nullize(text, true));
    }
    XDebuggerEvaluator evaluator = session.getDebugProcess().getEvaluator();
    if (evaluator != null) {
      return XDebuggerEvaluateActionHandler.getExpressionText(evaluator, editor.getProject(), editor).then(s -> StringUtil.nullize(s, true));
    }
    return Promises.resolvedPromise(null);
  }

  @Override
  protected void perform(@NotNull XDebugSession session, DataContext dataContext) {
    getTextToEvaluate(dataContext, session)
      .onSuccess(text -> {
        UIUtil.invokeLaterIfNeeded(() -> {
          XDebugSessionTab tab = ((XDebugSessionImpl)session).getSessionTab();
          if (tab != null) {
            final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
            XInlineWatchesViewImpl watchesView = (XInlineWatchesViewImpl)tab.getWatchesView();
            Set<Integer> processedLines = new HashSet<>();
            for (XSourcePosition position : XDebuggerUtilImpl.getAllCaretsPositions(session.getProject(), dataContext)) {
              if (processedLines.add(position.getLine())) {
                if (text != null) {
                  watchesView.addInlineWatchExpression(XExpressionImpl.fromText(text), -1, position, true);
                } else {
                  watchesView.showInplaceEditor(position, editor);
                }
              }
            }
          }
        });
      }).onError(e -> {

    });
  }
}
