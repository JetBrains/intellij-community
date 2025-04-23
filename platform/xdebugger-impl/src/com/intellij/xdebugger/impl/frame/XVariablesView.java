// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.components.BorderLayoutPanel;
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
  private final WeakReference<XDebugSessionProxy> myProxy;

  /**
   * @deprecated Use {@link XVariablesView#XVariablesView(XDebugSessionProxy)} instead
   */
  @Deprecated
  public XVariablesView(@NotNull XDebugSessionImpl session) {
    this(XDebugSessionProxyKeeperKt.asProxy(session));
  }

  public XVariablesView(@NotNull XDebugSessionProxy proxy) {
    super(proxy.getProject(), proxy.getEditorsProvider(), proxy.getValueMarkers());
    myProxy = new WeakReference<>(proxy);
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

  @ApiStatus.Internal
  @Override
  public void processSessionEvent(@NotNull SessionEvent event, @NotNull XDebugSessionProxy session) {
    if (ApplicationManager.getApplication().isDispatchThread()) { // mark nodes obsolete asap
      getTree().markNodesObsolete();
    }

    if (event == SessionEvent.STOPPED) {
      myProxy.clear();
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

  /**
   * @deprecated Use {@link XVariablesView#getSessionProxy()} instead
   */
  @Deprecated
  @ApiStatus.Internal
  public final @Nullable XDebugSessionImpl getSession() {
    XDebugSessionProxy proxy = getSessionProxy();
    if (proxy == null) return null;
    if (!(proxy instanceof XDebugSessionProxy.Monolith monolith)) {
      Logger.getInstance(XVariablesView.class).error("This method can be used only with monolith session proxies, got: " +
                                                     proxy + " of type " + proxy.getClass() + " instead");
      return null;
    }
    return (XDebugSessionImpl)monolith.getSession();
  }

  @ApiStatus.Internal
  public final @Nullable XDebugSessionProxy getSessionProxy() {
    return myProxy.get();
  }

  private void clearInlineData(XDebuggerTree tree) {
    InlineVariablesInfo.set(getSessionProxy(), null);
    tree.updateEditor();
    clearInlays(tree);
  }

  protected void addEmptyMessage(XValueContainerNode<?> root) {
    XDebugSessionProxy session = getSessionProxy();
    if (session == null) return;
    if (!session.isStopped() && session.isPaused()) {
      root.setInfoMessage(XDebuggerBundle.message("message.frame.is.not.available"), null);
    }
    else {
      root.setInfoMessage(session.getCurrentStateMessage(), session.getCurrentStateHyperlinkListener());
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
    XDebugSessionProxy session = getSessionProxy();
    XSourcePosition position = session == null ? null : session.getCurrentPosition();
    if (position != null) {
      sink.lazy(CommonDataKeys.VIRTUAL_FILE, () -> position.getFile());
    }
  }

  public static final class InlineVariablesInfo {
    private static final Key<InlineVariablesInfo> DEBUG_VARIABLES = Key.create("debug.variables");

    private List<InlineDebugRenderer> myInlays = null;

    @ApiStatus.Obsolete
    public static @Nullable InlineVariablesInfo get(@Nullable XDebugSession session) {
      if (session == null) return null;
     return get(XDebugSessionProxyKeeperKt.asProxy(session));
    }

    @ApiStatus.Internal
    public static @Nullable InlineVariablesInfo get(@Nullable XDebugSessionProxy session) {
      if (session != null) {
        return DEBUG_VARIABLES.get(session.getSessionData());
      }
      return null;
    }

    /**
     * Use {@link InlineVariablesInfo#set(XDebugSessionProxy, InlineVariablesInfo)} instead
     */
    @ApiStatus.Obsolete
    public static void set(@Nullable XDebugSession session, InlineVariablesInfo info) {
      if (session != null) {
        set(XDebugSessionProxyKeeperKt.asProxy(session), info);
      }
    }

    public static void set(@Nullable XDebugSessionProxy session, InlineVariablesInfo info) {
      if (session != null) {
        DEBUG_VARIABLES.set(session.getSessionData(), info);
      }
    }

    public void setInlays(List<InlineDebugRenderer> inlays) {
      myInlays = inlays;
    }

    public @NotNull List<InlineDebugRenderer> getInlays() {
      return ObjectUtils.notNull(myInlays, Collections::emptyList);
    }
  }
}
