// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.inline;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.AppUIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.XDebuggerWatchesManager;
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerTreeCreator;
import com.intellij.xdebugger.impl.evaluate.quick.common.DebuggerTreeCreator;
import com.intellij.xdebugger.impl.evaluate.quick.common.XDebuggerTextPopup;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promises;

import java.awt.*;
import java.util.Collections;

@ApiStatus.Experimental
public class XDebuggerTextInlayPopup<D> extends XDebuggerTextPopup<D> {

  private final @NotNull XSourcePosition myPosition;
  private final @NotNull XDebugSession mySession;
  private final @NotNull XValueNodeImpl myValueNode;

  private XDebuggerTextInlayPopup(@NotNull DebuggerTreeCreator<D> creator,
                                  @NotNull D initialItem,
                                  @NotNull Editor editor,
                                  @NotNull Point point,
                                  @NotNull XSourcePosition presentationPosition,
                                  @NotNull XDebugSession session,
                                  @Nullable Runnable hideRunnable,
                                  @NotNull XValueNodeImpl valueNode) {
    super(valueNode.getFullValueEvaluator(), creator, initialItem, editor, point, session.getProject(), hideRunnable);
    myPosition = presentationPosition;
    mySession = session;
    myValueNode = valueNode;
  }

  public static void showTextPopup(@NotNull String initialText,
                                   @NotNull XDebuggerTreeCreator creator,
                                   @NotNull Pair<XValue, String> initialItem,
                                   @NotNull XValueNodeImpl valueNode,
                                   @NotNull Editor editor,
                                   @NotNull Point point,
                                   @NotNull XSourcePosition position,
                                   @NotNull XDebugSession session,
                                   Runnable hideRunnable) {
    new XDebuggerTextInlayPopup<>(creator, initialItem, editor, point, position, session, hideRunnable, valueNode).show(initialText);
  }

  @Override
  protected DefaultActionGroup getToolbarActions() {
    DefaultActionGroup toolbarActions = super.getToolbarActions();
    if (Registry.is("debugger.watches.inline.enabled")) {
      AnAction watchAction = myValueNode instanceof InlineWatchNodeImpl
                             ? new EditInlineWatch()
                             : new AddInlineWatch();

      toolbarActions.add(watchAction, Constraints.LAST);
    }
    return toolbarActions;
  }

  @Override
  protected @NotNull XValueNodeImpl getNode() {
    return myValueNode;
  }

  @Override
  protected void showTreePopup(Runnable hideTreeRunnable) {
    XDebuggerTreeInlayPopup.showTreePopup(myTreeCreator, myInitialItem, myValueNode, myEditor, myPoint, myPosition, mySession, hideTreeRunnable);
  }

  private class EditInlineWatch extends AnAction {

    private EditInlineWatch() {
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
      watchesManager.inlineWatchesRemoved(Collections.singletonList(watch.getWatch()), null);
      watchesManager.showInplaceEditor(watch.getPosition(), myEditor, mySession, watch.getExpression());
      hideTextPopup();
    }
  }

  private class AddInlineWatch extends AnAction {

    private AddInlineWatch() {
      ActionUtil.mergeFrom(this, "Debugger.AddInlineWatch");
      Presentation presentation = getTemplatePresentation();
      presentation.setText(XDebuggerBundle.message("debugger.inline.watches.popup.action.add.as.inline.watch"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myValueNode.calculateEvaluationExpression()
        .thenAsync(expr -> {
          return Promises.resolvedPromise(expr);
        }).onSuccess(expr -> {
          AppUIUtil.invokeOnEdt(() -> {
            XDebuggerWatchesManager manager =
              ((XDebuggerManagerImpl)XDebuggerManager.getInstance(mySession.getProject())).getWatchesManager();
            manager.showInplaceEditor(myPosition, myEditor, mySession, expr);
            hideTextPopup();
          });
        });
    }
  }
}