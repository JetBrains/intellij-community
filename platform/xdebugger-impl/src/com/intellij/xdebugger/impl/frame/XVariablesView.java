/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger.impl.frame;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ObjectLongHashMap;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import gnu.trove.THashMap;
import gnu.trove.TObjectLongHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author nik
 */
public class XVariablesView extends XVariablesViewBase implements DataProvider {
  private final JPanel myComponent;

  public XVariablesView(@NotNull XDebugSessionImpl session) {
    super(session.getProject(), session.getDebugProcess().getEditorsProvider(), session.getValueMarkers());
    myComponent = new BorderLayoutPanel();
    myComponent.add(super.getPanel());
    DataManager.registerDataProvider(myComponent, this);
  }

  @Override
  public JPanel getPanel() {
    return myComponent;
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
    DebuggerUIUtil.invokeLater(() -> {
      getTree().markNodesObsolete();
      if (stackFrame != null) {
        cancelClear();
        buildTreeAndRestoreState(stackFrame);
      }
      else {
        requestClear();
      }
    });
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
        root.setInfoMessage("Frame is not available", null);
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
  public Object getData(@NonNls String dataId) {
    if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return getCurrentFile(getTree());
    }
    return null;
  }

  public static class InlineVariablesInfo {
    private final Map<Pair<VirtualFile, Integer>, Set<Entry>> myData = new THashMap<>();
    private final TObjectLongHashMap<VirtualFile> myTimestamps = new ObjectLongHashMap<>();
    private static final Key<InlineVariablesInfo> DEBUG_VARIABLES = Key.create("debug.variables");

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

    @Nullable
    public synchronized List<XValueNodeImpl> get(@NotNull VirtualFile file, int line, long currentTimestamp) {
      long timestamp = myTimestamps.get(file);
      if (timestamp == -1 || timestamp < currentTimestamp) {
        return null;
      }
      Set<Entry> entries = myData.get(Pair.create(file, line));
      if (entries == null) return null;
      return ContainerUtil.map(entries, entry -> entry.myNode);
    }

    public synchronized void put(@NotNull VirtualFile file, @NotNull XSourcePosition position, @NotNull XValueNodeImpl node, long timestamp) {
      myTimestamps.put(file, timestamp);
      Pair<VirtualFile, Integer> key = Pair.create(file, position.getLine());
      myData.computeIfAbsent(key, k -> new TreeSet<>()).add(new Entry(position.getOffset(), node));
    }

    private static class Entry implements Comparable<Entry> {
      private final long myOffset;
      private final XValueNodeImpl myNode;

      public Entry(long offset, @NotNull XValueNodeImpl node) {
        myOffset = offset;
        myNode = node;
      }

      @Override
      public int compareTo(Entry o) {
        if (myNode == o.myNode) return 0;
        int res = Comparing.compare(myOffset, o.myOffset);
        if (res == 0) {
          return XValueNodeImpl.COMPARATOR.compare(myNode, o.myNode);
        }
        return res;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Entry entry = (Entry)o;

        if (!myNode.equals(entry.myNode)) return false;

        return true;
      }

      @Override
      public int hashCode() {
        return myNode.hashCode();
      }
    }
  }
}
