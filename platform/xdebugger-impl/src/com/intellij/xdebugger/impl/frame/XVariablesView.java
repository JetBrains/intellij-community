/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.frame;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ObjectLongHashMap;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.intellij.xdebugger.impl.ui.tree.nodes.MessageTreeNode.createInfoMessage;

/**
 * @author nik
 */
public class XVariablesView extends XVariablesViewBase {
  public static final Key<InlineVariablesInfo> DEBUG_VARIABLES = Key.create("debug.variables");
  public static final Key<ObjectLongHashMap<VirtualFile>> DEBUG_VARIABLES_TIMESTAMPS = Key.create("debug.variables.timestamps");
  private final JComponent myComponent;

  public XVariablesView(@NotNull XDebugSessionImpl session) {
    super(session.getProject(), session.getDebugProcess().getEditorsProvider(), session.getValueMarkers());
    myComponent = new MyPanel();
    myComponent.add(super.getPanel());
  }

  @Override
  public JComponent getPanel() {
    return myComponent;
  }

  @Override
  public void processSessionEvent(@NotNull final SessionEvent event) {
    XDebugSession session = getSession(getPanel());
    XStackFrame stackFrame = session == null ? null : session.getCurrentStackFrame();
    XDebuggerTree tree = getTree();

    if (event == SessionEvent.BEFORE_RESUME || event == SessionEvent.SETTINGS_CHANGED) {
      saveCurrentTreeState(stackFrame);
      if (event == SessionEvent.BEFORE_RESUME) {
        return;
      }
    }

    tree.markNodesObsolete();
    if (stackFrame != null) {
      cancelClear();
      buildTreeAndRestoreState(stackFrame);
    }
    else {
      requestClear();
    }
  }

  @Override
  public void dispose() {
    clearInlineData(getTree());
    super.dispose();
  }

  private static void clearInlineData(XDebuggerTree tree) {
    tree.getProject().putUserData(DEBUG_VARIABLES, null);
    tree.getProject().putUserData(DEBUG_VARIABLES_TIMESTAMPS, null);
    tree.updateEditor();
  }

  @Override
  protected void clear() {
    XDebuggerTree tree = getTree();
    tree.setSourcePosition(null);
    clearInlineData(tree);

    XDebuggerTreeNode node;
    XDebugSession session = getSession(getPanel());
    if (session == null || (!session.isStopped() && session.isPaused())) {
      node = createInfoMessage(tree, "Frame is not available");
    }
    else {
      XDebugProcess debugProcess = session.getDebugProcess();
      node = createInfoMessage(tree, debugProcess.getCurrentStateMessage(), debugProcess.getCurrentStateHyperlinkListener());
    }
    tree.setRoot(node, true);
    super.clear();
  }

  public static class InlineVariablesInfo {
    private final Map<Pair<VirtualFile, Integer>, Set<Entry>> myData
      = new THashMap<Pair<VirtualFile, Integer>, Set<Entry>>();

    @Nullable
    public List<XValueNodeImpl> get(@NotNull VirtualFile file, int line) {
      synchronized (myData) {
        Set<Entry> entries = myData.get(Pair.create(file, line));
        if (entries == null) return null;
        return ContainerUtil.map(entries, new Function<Entry, XValueNodeImpl>() {
          @Override
          public XValueNodeImpl fun(Entry entry) {
            return entry.myNode;
          }
        });
      }
    }

    public void put(@NotNull VirtualFile file, @NotNull XSourcePosition position, @NotNull XValueNodeImpl node) {
      synchronized (myData) {
        Pair<VirtualFile, Integer> key = Pair.create(file, position.getLine());
        Set<Entry> entries = myData.get(key);
        if (entries == null) {
          entries = new TreeSet<Entry>();
          myData.put(key, entries);
        }
        entries.add(new Entry(position.getOffset(), node));
      }
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

  private class MyPanel extends BorderLayoutPanel implements DataProvider {
    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
      if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
        return getCurrentFile(getTree());
      }
      return null;
    }
  }
}
