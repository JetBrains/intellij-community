// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
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
  protected BorderLayoutPanel myComponent;
  private final WeakReference<XDebugSessionImpl> mySession;

  public XVariablesView(@NotNull XDebugSessionImpl session) {
    super(session.getProject(), session.getDebugProcess().getEditorsProvider(), session.getValueMarkers());
    mySession = new WeakReference<>(session);
    myComponent = new BorderLayoutPanel();
    JComponent panel = super.getPanel();
    JComponent top = createTopPanel();
    if (top != null) {
      panel = new BorderLayoutPanel().addToTop(top).addToCenter(panel);
    }
    myComponent.add(panel);
    DataManager.registerDataProvider(myComponent, this);
  }

  @Override
  public JPanel getPanel() {
    return myComponent;
  }

  JComponent createTopPanel() {
    return null;
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
    private final Map<Pair<VirtualFile, Integer>, Set<Entry>> myData = new HashMap<>();
    private final Object2LongMap<VirtualFile> myTimestamps = new Object2LongOpenHashMap<>();
    private static final Key<InlineVariablesInfo> DEBUG_VARIABLES = Key.create("debug.variables");

    public InlineVariablesInfo() {
      myTimestamps.defaultReturnValue(-1);
    }

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
      long timestamp = myTimestamps.getLong(file);
      if (timestamp == -1 || timestamp < currentTimestamp) {
        return null;
      }
      Set<Entry> entries = myData.get(new Pair<>(file, line));
      if (entries == null) return null;
      return ContainerUtil.map(entries, entry -> entry.myNode);
    }

    public synchronized void put(@NotNull VirtualFile file, @NotNull XSourcePosition position, @NotNull XValueNodeImpl node, long timestamp) {
      myTimestamps.put(file, timestamp);
      Pair<VirtualFile, Integer> key = new Pair<>(file, position.getLine());
      myData.computeIfAbsent(key, k -> new TreeSet<>()).add(new Entry(position.getOffset(), node));
    }

    private static class Entry implements Comparable<Entry> {
      private final long myOffset;
      private final XValueNodeImpl myNode;

      Entry(long offset, @NotNull XValueNodeImpl node) {
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
