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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.nodes.MessageTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.RestorableStateNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * @author nik
 */
public class XDebuggerTree extends DnDAwareTree implements DataProvider, Disposable {
  private static final DataKey<XDebuggerTree> XDEBUGGER_TREE_KEY = DataKey.create("xdebugger.tree");
  private static final Convertor<TreePath, String> SPEED_SEARCH_CONVERTER = new Convertor<TreePath, String>() {
    @Override
    public String convert(TreePath o) {
      String text = null;
      if (o != null) {
        final Object node = o.getLastPathComponent();
        if (node instanceof RestorableStateNode) {
          text = ((RestorableStateNode)node).getName();
        }
        else if (node instanceof XDebuggerTreeNode) {
          text = ((XDebuggerTreeNode)node).getText().toString();
        }
      }
      return StringUtil.notNullize(text);
    }
  };
  private final DefaultTreeModel myTreeModel;
  private final Project myProject;
  private final XDebuggerEditorsProvider myEditorsProvider;
  private XSourcePosition mySourcePosition;
  private final List<XDebuggerTreeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final XValueMarkers<?,?> myValueMarkers;

  public XDebuggerTree(final @NotNull Project project,
                       final @NotNull XDebuggerEditorsProvider editorsProvider,
                       final @Nullable XSourcePosition sourcePosition,
                       final @NotNull String popupActionGroupId, @Nullable XValueMarkers<?, ?> valueMarkers) {
    myValueMarkers = valueMarkers;
    myProject = project;
    myEditorsProvider = editorsProvider;
    mySourcePosition = sourcePosition;
    myTreeModel = new DefaultTreeModel(null);
    setModel(myTreeModel);
    setCellRenderer(new XDebuggerTreeRenderer());
    new TreeLinkMouseListener(new XDebuggerTreeRenderer()) {
      @Override
      protected void handleTagClick(Object tag, MouseEvent event) {
        if (tag instanceof XDebuggerTreeNodeHyperlink) {
          ((XDebuggerTreeNodeHyperlink)tag).onClick(event);
        }
      }
    }.installOn(this);
    setRootVisible(false);
    setShowsRootHandles(true);

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        expandIfEllipsis();
        return true;
      }
    }.installOn(this);

    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE || key == KeyEvent.VK_RIGHT) {
          expandIfEllipsis();
        }
      }
    });

    if (Boolean.valueOf(System.getProperty("xdebugger.variablesView.rss"))) {
      new XDebuggerTreeSpeedSearch(this, SPEED_SEARCH_CONVERTER);
    }
    else {
      new TreeSpeedSearch(this, SPEED_SEARCH_CONVERTER);
    }

    final ActionManager actionManager = ActionManager.getInstance();
    addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(final Component comp, final int x, final int y) {
        ActionGroup group = (ActionGroup)actionManager.getAction(popupActionGroupId);
        actionManager.createActionPopupMenu(ActionPlaces.UNKNOWN, group).getComponent().show(comp, x, y);
      }
    });
    registerShortcuts();
  }

  private void expandIfEllipsis() {
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

  @Nullable
  public XValueMarkers<?, ?> getValueMarkers() {
    return myValueMarkers;
  }

  public DefaultTreeModel getTreeModel() {
    return myTreeModel;
  }

  @Override
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

  public void childrenLoaded(final @NotNull XDebuggerTreeNode node,
                             final @NotNull List<XValueContainerNode<?>> children,
                             final boolean last) {
    for (XDebuggerTreeListener listener : myListeners) {
      listener.childrenLoaded(node, children, last);
    }
  }

  public void nodeLoaded(final @NotNull RestorableStateNode node, final @NotNull String name) {
    for (XDebuggerTreeListener listener : myListeners) {
      listener.nodeLoaded(node, name);
    }
  }

  public void markNodesObsolete() {
    Object root = myTreeModel.getRoot();
    if (root instanceof XValueContainerNode<?>) {
      markNodesObsolete((XValueContainerNode<?>)root);
    }
  }

  @Override
  public void dispose() {
    ActionManager actionManager = ActionManager.getInstance();
    actionManager.getAction(XDebuggerActions.SET_VALUE).unregisterCustomShortcutSet(this);
    actionManager.getAction(XDebuggerActions.COPY_VALUE).unregisterCustomShortcutSet(this);
    actionManager.getAction(XDebuggerActions.JUMP_TO_SOURCE).unregisterCustomShortcutSet(this);
    actionManager.getAction(XDebuggerActions.JUMP_TO_TYPE_SOURCE).unregisterCustomShortcutSet(this);
    actionManager.getAction(XDebuggerActions.MARK_OBJECT).unregisterCustomShortcutSet(this);
  }

  private void registerShortcuts() {
    ActionManager actionManager = ActionManager.getInstance();
    actionManager.getAction(XDebuggerActions.SET_VALUE)
      .registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)), this);
    actionManager.getAction(XDebuggerActions.COPY_VALUE).registerCustomShortcutSet(CommonShortcuts.getCopy(), this);
    actionManager.getAction(XDebuggerActions.JUMP_TO_SOURCE).registerCustomShortcutSet(CommonShortcuts.getEditSource(), this);
    Shortcut[] editTypeShortcuts = KeymapManager.getInstance().getActiveKeymap().getShortcuts(XDebuggerActions.EDIT_TYPE_SOURCE);
    actionManager.getAction(XDebuggerActions.JUMP_TO_TYPE_SOURCE).registerCustomShortcutSet(new CustomShortcutSet(editTypeShortcuts), this);
    actionManager.getAction(XDebuggerActions.MARK_OBJECT)
      .registerCustomShortcutSet(new CustomShortcutSet(KeymapManager.getInstance().getActiveKeymap().getShortcuts("ToggleBookmark")), this);
  }

  private static void markNodesObsolete(final XValueContainerNode<?> node) {
    node.setObsolete();
    List<? extends XValueContainerNode<?>> loadedChildren = node.getLoadedChildren();
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
