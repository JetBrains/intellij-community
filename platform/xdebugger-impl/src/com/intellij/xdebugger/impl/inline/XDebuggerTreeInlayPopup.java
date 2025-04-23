// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.inline;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.AppUIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.XDebuggerWatchesManager;
import com.intellij.xdebugger.impl.evaluate.quick.common.DebuggerTreeCreator;
import com.intellij.xdebugger.impl.evaluate.quick.common.XDebuggerTreePopup;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxyKeeperKt;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promises;

import java.awt.*;
import java.util.Collections;

public class XDebuggerTreeInlayPopup<D> extends XDebuggerTreePopup<D> {
  private final @NotNull XSourcePosition myPresentationPosition;
  private final @NotNull XDebugSessionProxy mySession;
  private final @NotNull XValueNodeImpl myValueNode;

  private XDebuggerTreeInlayPopup(@NotNull DebuggerTreeCreator<D> creator,
                                  @NotNull Editor editor,
                                  @NotNull Point point,
                                  @NotNull XSourcePosition presentationPosition,
                                  @NotNull XDebugSessionProxy session,
                                  @Nullable Runnable hideRunnable,
                                  @NotNull XValueNodeImpl valueNode) {
    super(creator, editor, point, session.getProject(), hideRunnable);
    myPresentationPosition = presentationPosition;
    mySession = session;
    myValueNode = valueNode;
  }

  @Override
  protected @NotNull DefaultActionGroup getToolbarActions() {
    DefaultActionGroup toolbarActions = super.getToolbarActions();
    if (Registry.is("debugger.watches.inline.enabled")) {
      AnAction watchAction = myValueNode instanceof InlineWatchNodeImpl
                             ? new EditInlineWatch()
                             : new AddInlineWatch();

      toolbarActions.add(watchAction, Constraints.LAST);
    }
    return toolbarActions;
  }

  private class AddInlineWatch extends XDebuggerTreeActionBase {

    private AddInlineWatch() {
      ActionUtil.mergeFrom(this, "Debugger.AddInlineWatch");
      Presentation presentation = getTemplatePresentation();
      presentation.setText(XDebuggerBundle.message("debugger.inline.watches.popup.action.add.as.inline.watch"));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
      node.calculateEvaluationExpression()
        .thenAsync(expr -> {
          if (expr == null && node != myValueNode) {
            return myValueNode.calculateEvaluationExpression();
          }
          else {
            return Promises.resolvedPromise(expr);
          }
        }).onSuccess(expr -> {
          AppUIUtil.invokeOnEdt(() -> {
            XDebuggerWatchesManager manager =
              ((XDebuggerManagerImpl)XDebuggerManager.getInstance(mySession.getProject())).getWatchesManager();
            manager.showInplaceEditor(myPresentationPosition, myEditor, mySession, expr);
          });
        });
    }
  }

  private class EditInlineWatch extends AnAction {

    EditInlineWatch() {
      super(XDebuggerBundle.message("debugger.inline.watches.edit.watch.expression.text"));
    }

    @Override
    public boolean displayTextInToolbar() {
      return true;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      InlineWatchNodeImpl watch = (InlineWatchNodeImpl)myValueNode;
      XDebuggerWatchesManager watchesManager =
        ((XDebuggerManagerImpl)XDebuggerManager.getInstance(mySession.getProject())).getWatchesManager();
      XDebugSessionProxy session = DebuggerUIUtil.getSessionProxy(e);
      if (session != null) {
        if (myPopup != null) {
          myPopup.cancel();
        }
        watchesManager.inlineWatchesRemoved(Collections.singletonList(watch.getWatch()), null);
        watchesManager.showInplaceEditor(watch.getPosition(), myEditor, session, watch.getExpression());
      }
    }
  }

  /**
   * Use {@link #showTreePopup(DebuggerTreeCreator, Object, XValueNodeImpl, Editor, Point, XSourcePosition, XDebugSessionProxy, Runnable)} instead.
   */
  @ApiStatus.Obsolete
  public static <D> void showTreePopup(DebuggerTreeCreator<D> creator,
                                       D initialItem,
                                       XValueNodeImpl valueNode,
                                       @NotNull Editor editor,
                                       @NotNull Point point,
                                       @NotNull XSourcePosition position,
                                       @NotNull XDebugSession session,
                                       Runnable hideRunnable) {
    XDebugSessionProxy proxy = XDebugSessionProxyKeeperKt.asProxy(session);
    new XDebuggerTreeInlayPopup<>(creator, editor, point, position, proxy, hideRunnable, valueNode).show(initialItem);
  }

  @ApiStatus.Internal
  public static <D> void showTreePopup(DebuggerTreeCreator<D> creator,
                                       D initialItem,
                                       XValueNodeImpl valueNode,
                                       @NotNull Editor editor,
                                       @NotNull Point point,
                                       @NotNull XSourcePosition position,
                                       @NotNull XDebugSessionProxy session,
                                       Runnable hideRunnable) {
    new XDebuggerTreeInlayPopup<>(creator, editor, point, position, session, hideRunnable, valueNode).show(initialItem);
  }
}