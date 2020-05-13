// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.frame.XWatchesViewImpl;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class XAddToWatchesFromEditorActionHandler extends XDebuggerActionHandler {
  @Override
  protected boolean isEnabled(@NotNull XDebugSession session, DataContext dataContext) {
    Promise<String> textPromise = getTextToEvaluate(dataContext, session);
    // in the case of async expression evaluation just enable the action
    if (textPromise.getState() == Promise.State.PENDING) {
      return true;
    }
    // or disable on rejected
    else if (textPromise.getState() == Promise.State.REJECTED) {
      return false;
    }
    // else the promise is already fulfilled, get it's value
    try {
      return textPromise.blockingGet(0) != null;
    }
    catch (TimeoutException | ExecutionException e) {
      return false;
    }
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
        if (text == null) return;
        UIUtil.invokeLaterIfNeeded(() -> {
          XDebugSessionTab tab = ((XDebugSessionImpl)session).getSessionTab();
          if (tab != null) {
            ((XWatchesViewImpl)tab.getWatchesView()).addWatchExpression(XExpressionImpl.fromText(text), -1, true, true);
          }
        });
      });
  }
}
