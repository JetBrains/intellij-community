// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
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
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.ref.WeakReference;
import java.util.*;

public class XVariablesView extends XVariablesViewBase implements DataProvider {
  protected final JPanel myComponent;
  protected final WeakReference<XDebugSessionImpl> mySession;

  public XVariablesView(@NotNull XDebugSessionImpl session) {
    super(session.getProject(), session.getDebugProcess().getEditorsProvider(), session.getValueMarkers());
    mySession = new WeakReference<>(session);
    myComponent = createMainPanel(super.getPanel());
    DataManager.registerDataProvider(myComponent, this);
  }

  protected JPanel createMainPanel(JComponent localsPanel) {
    return new BorderLayoutPanel().addToCenter(localsPanel);
  }

  @Override
  public JPanel getPanel() {
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

  protected void addEmptyMessage(XValueContainerNode root) {
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

    XValueContainerNode root = createNewRootNode(null);
    addEmptyMessage(root);
    super.clear();
  }

  @Nullable
  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      XDebugSessionImpl session = mySession.get();
      if (session != null) {
        XSourcePosition position = session.getCurrentPosition();
        if (position != null) {
          return position.getFile();
        }
      }
    }
    return null;
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
