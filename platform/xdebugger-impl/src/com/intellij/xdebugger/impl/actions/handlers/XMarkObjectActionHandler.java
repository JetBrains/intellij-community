// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions.handlers;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.actions.MarkObjectActionHandler;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkerPresentationDialog;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.CONTEXT_COMPONENT;

@ApiStatus.Internal
public class XMarkObjectActionHandler extends MarkObjectActionHandler {
  @Override
  public void perform(@NotNull Project project, @NotNull AnActionEvent event) {
    XDebugSession session = DebuggerUIUtil.getSession(event);
    if (session == null) return;

    XValueMarkers<?, ?> markers = ((XDebugSessionImpl)session).getValueMarkers();
    XValueNodeImpl node = XDebuggerTreeActionBase.getSelectedNode(event.getDataContext());
    if (markers == null || node == null) return;
    XValue value = node.getValueContainer();

    boolean detachedView = DebuggerUIUtil.isInDetachedTree(event);
    XDebuggerTreeState treeState = XDebuggerTreeState.saveState(node.getTree());

    ValueMarkup existing = markers.getMarkup(value);
    Promise<Object> markPromise;
    if (existing != null) {
      markPromise = markers.unmarkValue(value);
    }
    else {
      Component component = event.getData(CONTEXT_COMPONENT);
      Window window = ComponentUtil.getWindow(component);
      if (!(window instanceof JFrame) && !(window instanceof JDialog)) {
        component = window.getOwner();
      }
      ValueMarkerPresentationDialog dialog = new ValueMarkerPresentationDialog(
        component, node.getName(), markers.getAllMarkers().values());
      dialog.show();
      ValueMarkup markup = dialog.getConfiguredMarkup();
      if (dialog.isOK() && markup != null) {
        markPromise = markers.markValue(value, markup);
      } else {
        return;
      }
    }
    markPromise.onSuccess(__ ->
      UIUtil.invokeLaterIfNeeded(() -> {
        if (detachedView) {
          node.getTree().rebuildAndRestore(treeState);
        }
        session.rebuildViews();
      })
    );
  }

  @Override
  public boolean isEnabled(@NotNull Project project, @NotNull AnActionEvent event) {
    XValueMarkers<?, ?> markers = getValueMarkers(event);
    if (markers == null) return false;

    XValue value = XDebuggerTreeActionBase.getSelectedValue(event.getDataContext());
    return value != null && markers.canMarkValue(value);
  }

  @Override
  public boolean isMarked(@NotNull Project project, @NotNull AnActionEvent event) {
    XValueMarkers<?, ?> markers = getValueMarkers(event);
    if (markers == null) return false;

    XValue value = XDebuggerTreeActionBase.getSelectedValue(event.getDataContext());
    return value != null && markers.getMarkup(value) != null;
  }

  @Override
  public boolean isHidden(@NotNull Project project, @NotNull AnActionEvent event) {
    return getValueMarkers(event) == null;
  }

  @Nullable
  private static XValueMarkers<?, ?> getValueMarkers(AnActionEvent event) {
    XDebugSession session = DebuggerUIUtil.getSession(event);
    return session != null ? ((XDebugSessionImpl)session).getValueMarkers() : null;
  }
}