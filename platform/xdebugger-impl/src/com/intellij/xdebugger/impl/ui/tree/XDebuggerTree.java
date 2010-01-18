/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.ui.tree.nodes.MessageTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author nik
 */
public class XDebuggerTree extends DnDAwareTree implements DataProvider {
  private static final DataKey<XDebuggerTree> XDEBUGGER_TREE_KEY = DataKey.create("xdebugger.tree");
  private static final Convertor<TreePath,String> SPEED_SEARCH_CONVERTER = new Convertor<TreePath, String>() {
    public String convert(TreePath o) {
      final Object node = o.getLastPathComponent();
      String text = null;
      if (node instanceof XValueNodeImpl) {
        text = ((XValueNodeImpl)node).getName();
      }
      else if (node instanceof XDebuggerTreeNode) {
        text = ((XDebuggerTreeNode)node).getText().toString();
      }
      return text != null ? text : "";
    }
  };
  private final DefaultTreeModel myTreeModel;
  private final Project myProject;
  private final XDebuggerEditorsProvider myEditorsProvider;
  private XSourcePosition mySourcePosition;
  private final List<XDebuggerTreeListener> myListeners = ContainerUtil.createEmptyCOWList();
  private final XDebugSession mySession;

  public XDebuggerTree(final @NotNull XDebugSession session, final @NotNull XDebuggerEditorsProvider editorsProvider, final @Nullable XSourcePosition sourcePosition) {
    mySession = session;
    myProject = session.getProject();
    myEditorsProvider = editorsProvider;
    mySourcePosition = sourcePosition;
    myTreeModel = new DefaultTreeModel(null);
    setModel(myTreeModel);
    setCellRenderer(new XDebuggerTreeRenderer());
    setRootVisible(false);
    setShowsRootHandles(true);
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(final MouseEvent e) {
        if (e.getClickCount() == 2) {
          MessageTreeNode[] treeNodes = getSelectedNodes(MessageTreeNode.class, null);
          if (treeNodes.length == 1) {
            MessageTreeNode node = treeNodes[0];
            if (node.isEllipsis()) {
              TreeNode parent = node.getParent();
              if (parent instanceof XValueContainerNode) {
                ((XValueContainerNode)parent).startComputingChildren();
              }
            }
          }
        }
      }
    });
    new TreeSpeedSearch(this, SPEED_SEARCH_CONVERTER);
  }

  public void addTreeListener(@NotNull XDebuggerTreeListener listener) {
    myListeners.add(listener);
  }

  public void removeTreeListener(@NotNull XDebuggerTreeListener listener) {
    myListeners.remove(listener);
  }

  public void setRoot(XDebuggerTreeNode root, final boolean rootVisible) {
    setRootVisible(rootVisible);
    myTreeModel.setRoot(root);
  }

  public XDebuggerTreeNode getRoot() {
    return (XDebuggerTreeNode)myTreeModel.getRoot();
  }

  @Nullable
  public XSourcePosition getSourcePosition() {
    return mySourcePosition;
  }

  public void setSourcePosition(final @Nullable XSourcePosition sourcePosition) {
    mySourcePosition = sourcePosition;
  }

  @NotNull
  public XDebuggerEditorsProvider getEditorsProvider() {
    return myEditorsProvider;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public XDebugSession getSession() {
    return mySession;
  }

  public DefaultTreeModel getTreeModel() {
    return myTreeModel;
  }

  @Nullable
  public Object getData(@NonNls final String dataId) {
    if (XDEBUGGER_TREE_KEY.is(dataId)) {
      return this;
    }
    return null;
  }

  public void rebuildAndRestore(final XDebuggerTreeState treeState) {
    Object rootNode = myTreeModel.getRoot();
    if (rootNode instanceof XDebuggerTreeNode) {
      ((XDebuggerTreeNode)rootNode).clearChildren();
      treeState.restoreState(this);
      repaint();
    }
  }

  public void childrenLoaded(final @NotNull XDebuggerTreeNode node, final @NotNull List<XValueContainerNode<?>> children, final boolean last) {
    for (XDebuggerTreeListener listener : myListeners) {
      listener.childrenLoaded(node, children, last);
    }
  }

  public void nodeLoaded(final @NotNull XValueNodeImpl node, final @NotNull String name, final @NotNull String value) {
    for (XDebuggerTreeListener listener : myListeners) {
      listener.nodeLoaded(node, name, value);
    }
  }

  public void markNodesObsolete() {
    Object root = myTreeModel.getRoot();
    if (root instanceof XValueContainerNode<?>) {
      markNodesObsolete((XValueContainerNode<?>)root);
    }
  }

  private static void markNodesObsolete(final XValueContainerNode<?> node) {
    node.setObsolete();
    List<XValueContainerNode<?>> loadedChildren = node.getLoadedChildren();
    if (loadedChildren != null) {
      for (XValueContainerNode<?> child : loadedChildren) {
        markNodesObsolete(child);
      }
    }
  }

  @Nullable
  public static XDebuggerTree getTree(final AnActionEvent e) {
    return e.getData(XDEBUGGER_TREE_KEY);
  }

  @Nullable
  public static XDebuggerTree getTree(DataContext context) {
    return XDEBUGGER_TREE_KEY.getData(context);
  }
}
