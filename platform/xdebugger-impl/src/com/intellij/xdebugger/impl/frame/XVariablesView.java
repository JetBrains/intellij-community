// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.inline.InlineDebugRenderer;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public class XVariablesView extends XVariablesViewBase {
  protected final JComponent myComponent;
  protected final WeakReference<XDebugSessionImpl> mySession;

  public XVariablesView(@NotNull XDebugSessionImpl session) {
    super(session.getProject(), session.getDebugProcess().getEditorsProvider(), session.getValueMarkers());
    mySession = new WeakReference<>(session);
    myComponent = UiDataProvider.wrapComponent(createMainPanel(super.getPanel()), sink -> uiDataSnapshot(sink));
  }

  protected JPanel createMainPanel(JComponent localsPanel) {
    return new BorderLayoutPanel().addToCenter(localsPanel);
  }

  @Override
  public JComponent getPanel() {
    return myComponent;
  }

  @Override
  public JComponent getMainComponent() {
    return getPanel();
  }

  protected void beforeTreeBuild(@NotNull SessionEvent event) {
  }

  @Override
  public void processSessionEvent(@NotNull SessionEvent event, @NotNull XDebugSession session) {
    if (ApplicationManager.getApplication().isDispatchThread()) { // mark nodes obsolete asap
      getTree().markNodesObsolete();
    }

    if (event == SessionEvent.BEFORE_RESUME) {
      return;
    }

    XStackFrame stackFrame = session.getCurrentStackFrame();
    ApplicationManager.getApplication().invokeLater(() -> {
      getTree().markNodesObsolete();
      if (stackFrame != null) {
        cancelClear();
        beforeTreeBuild(event);
        buildTreeAndRestoreState(stackFrame);
      }
      else {
        requestClear();
      }
    }, session.getProject().getDisposed());
  }

  @Override
  public void dispose() {
    clearInlineData(getTree());
    super.dispose();
  }

  private static void clearInlineData(XDebuggerTree tree) {
    InlineVariablesInfo.set(getSession(tree), null);
    tree.updateEditor();
    clearInlays(tree);
  }

  protected void addEmptyMessage(XValueContainerNode<?> root) {
    XDebugSession session = getSession(getPanel());
    if (session != null) {
      if (!session.isStopped() && session.isPaused()) {
        root.setInfoMessage(XDebuggerBundle.message("message.frame.is.not.available"), null);
      }
      else {
        XDebugProcess debugProcess = session.getDebugProcess();
        root.setInfoMessage(debugProcess.getCurrentStateMessage(), debugProcess.getCurrentStateHyperlinkListener());
      }
    }
  }

  @Override
  protected void clear() {
    XDebuggerTree tree = getTree();
    tree.setSourcePosition(null);
    clearInlineData(tree);

    XValueContainerNode<?> root = createNewRootNode(null);
    addEmptyMessage(root);
    super.clear();
  }

  protected void uiDataSnapshot(@NotNull DataSink sink) {
    XDebugSessionImpl session = mySession.get();
    XSourcePosition position = session == null ? null : session.getCurrentPosition();
    if (position != null) {
      sink.lazy(CommonDataKeys.VIRTUAL_FILE, () -> position.getFile());
    }
  }

  public static final class InlineVariablesInfo {
    private static final Key<InlineVariablesInfo> DEBUG_VARIABLES = Key.create("debug.variables");

    private List<InlineDebugRenderer> myInlays = null;

    @Nullable
    public static InlineVariablesInfo get(@Nullable XDebugSession session) {
      if (session != null) {
        return DEBUG_VARIABLES.get(((XDebugSessionImpl)session).getSessionData());
      }
      return null;
    }

    public static void set(@Nullable XDebugSession session, InlineVariablesInfo info) {
      if (session != null) {
        DEBUG_VARIABLES.set(((XDebugSessionImpl)session).getSessionData(), info);
      }
    }

    public void setInlays(List<InlineDebugRenderer> inlays) {
      myInlays = inlays;
    }

    @NotNull
    public List<InlineDebugRenderer> getInlays() {
      return ObjectUtils.notNull(myInlays, Collections::emptyList);
    }
  }
}
