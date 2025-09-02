// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.XDebuggerWatchesManager;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxyKeeperKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import static com.intellij.xdebugger.impl.actions.handlers.XAddToWatchesFromEditorActionHandler.getTextToEvaluate;

@ApiStatus.Internal
public class XAddToInlineWatchesFromEditorActionHandler extends XDebuggerActionHandler {
  @Override
  protected boolean isEnabled(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    return editor != null && Registry.is("debugger.watches.inline.enabled");
  }

  @Override
  protected void perform(@NotNull XDebugSession session, @NotNull DataContext dataContext) {
    final Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor == null) return;

    getTextToEvaluate(dataContext, session)
      .onSuccess(text -> {
        UIUtil.invokeLaterIfNeeded(() -> {
          XDebuggerWatchesManager watchesManager = ((XDebuggerManagerImpl)XDebuggerManager.getInstance(session.getProject())).getWatchesManager();
          XSourcePosition caretPosition = XDebuggerUtilImpl.getCaretPosition(session.getProject(), dataContext);
          if (text != null) {
            watchesManager.addInlineWatchExpression(XExpressionImpl.fromText(text), -1, caretPosition, false);
          } else if (caretPosition != null) {
            watchesManager.showInplaceEditor(caretPosition, editor, XDebugSessionProxyKeeperKt.asProxy(session), null);
          }
        });
      }).onError(e -> { });
  }
}
