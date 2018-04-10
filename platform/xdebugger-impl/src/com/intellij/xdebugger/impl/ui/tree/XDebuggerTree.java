/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.execution.configurations.RemoteRunProfile;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.DataManager;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.SingleAlarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import com.intellij.util.containers.TransferToEDTQueue;
import com.intellij.util.ui.TextTransferable;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.impl.ui.tree.nodes.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.util.List;

/**
 * @author nik
 */
public class XDebuggerTree extends DnDAwareTree implements DataProvider, Disposable {
  private final TransferToEDTQueue<Runnable> myLaterInvocator = TransferToEDTQueue.createRunnableMerger("XDebuggerTree later invocator");

  private final ComponentListener myMoveListener = new ComponentAdapter() {
    @Override
    public void componentMoved(ComponentEvent e) {
      repaint(); // needed to repaint links in cell renderer on horizontal scrolling
    }
  };

  private static final DataKey<XDebuggerTree> XDEBUGGER_TREE_KEY = DataKey.create("xdebugger.tree");
  private final SingleAlarm myAlarm = new SingleAlarm(new Runnable() {
    @Override
    public void run() {
      DebuggerUIUtil.repaintCurrentEditor(myProject);
    }
  }, 100, this);

  private static final Convertor<TreePath, String> SPEED_SEARCH_CONVERTER = o -> {
    String text = null;
    if (o != null) {
      final Object node = o.getLastPathComponent();
      if (node instanceof XDebuggerTreeNode) {
        text = ((XDebuggerTreeNode)node).getText().toString();
      }
    }
    return StringUtil.notNullize(text);
  };

  private static final TransferHandler DEFAULT_TRANSFER_HANDLER = new TransferHandler() {
    @Override
    protected Transferable createTransferable(JComponent c) {
      if (!(c instanceof XDebuggerTree)) {
        return null;
      }
      XDebuggerTree tree = (XDebuggerTree)c;
      //noinspection deprecation
      TreePath[] selectedPaths = tree.getSelectionPaths();
      if (selectedPaths == null || selectedPaths.length == 0) {
        return null;
      }

      StringBuilder plainBuf = new StringBuilder();
      StringBuilder htmlBuf = new StringBuilder();
      htmlBuf.append("<html>\n<body>\n<ul>\n");
      TextTransferable.ColoredStringBuilder coloredTextContainer = new TextTransferable.ColoredStringBuilder();
      for (TreePath path : selectedPaths) {
        htmlBuf.append("  <li>");
        Object node = path.getLastPathComponent();
        if (node != null) {
          if (node instanceof XDebuggerTreeNode) {
            ((XDebuggerTreeNode)node).appendToComponent(coloredTextContainer);
            coloredTextContainer.appendTo(plainBuf, htmlBuf);
          }
          else {
            String text = node.toString();
            plainBuf.append(text);
            htmlBuf.append(text);
          }
        }
        plainBuf.append('\n');
        htmlBuf.append("</li>\n");
      }

      // remove the last newline
      plainBuf.setLength(plainBuf.length() - 1);
      htmlBuf.append("</ul>\n</body>\n</html>");
      return new TextTransferable(htmlBuf.toString(), plainBuf.toString());
    }

    @Override
    public int getSourceActions(JComponent c) {
      return COPY;
    }
  };

  private final DefaultTreeModel myTreeModel;
  private final Project myProject;
  private final XDebuggerEditorsProvider myEditorsProvider;
  private XSourcePosition mySourcePosition;
  private final List<XDebuggerTreeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final XValueMarkers<?,?> myValueMarkers;
  private final TreeExpansionListener myTreeExpansionListener;

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
      protected boolean doCacheLastNode() {
        return false;
      }

      @Override
      protected void handleTagClick(@Nullable Object tag, @NotNull MouseEvent event) {
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
        return expandIfEllipsis();
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

    new AnAction() {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        TreePath[] paths = getSelectionPaths();
        if (paths != null) {
          for (TreePath path : paths) {
            Object component = path.getLastPathComponent();
            if (component instanceof XDebuggerTreeNode) {
              XDebuggerTreeNodeHyperlink link = ((XDebuggerTreeNode)component).getLink();
              if (link != null) {
                // dummy event
                link.onClick(new MouseEvent(XDebuggerTree.this, 0,0,0,0,0,1,false));
              }
            }
          }
        }
      }
    }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0)), this, this);

    setTransferHandler(DEFAULT_TRANSFER_HANDLER);

    addComponentListener(myMoveListener);


    myTreeExpansionListener = new TreeExpansionListener() {
      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        handleExpansion(event, true);
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {
        handleExpansion(event, false);
      }

      private void handleExpansion(TreeExpansionEvent event, boolean expanded) {
        final TreePath path = event.getPath();
        final Object component = path != null ? path.getLastPathComponent() : null;
        if (component instanceof XValueGroupNodeImpl) {
          ((XValueGroupNodeImpl)component).onExpansion(expanded);
        }
      }
    };
    addTreeExpansionListener(myTreeExpansionListener);
  }

  public void updateEditor() {
    myAlarm.cancelAndRequest();
  }

  public boolean isUnderRemoteDebug() {
    DataContext context = DataManager.getInstance().getDataContext(this);
    ExecutionEnvironment env = LangDataKeys.EXECUTION_ENVIRONMENT.getData(context);
    if (env != null && env.getRunProfile() instanceof RemoteRunProfile) {
      return true;
    }
    return false;
  }

  private boolean expandIfEllipsis() {
    MessageTreeNode[] treeNodes = getSelectedNodes(MessageTreeNode.class, null);
    if (treeNodes.length == 1) {
      MessageTreeNode node = treeNodes[0];
      if (node.isEllipsis()) {
        TreeNode parent = node.getParent();
        if (parent instanceof XValueContainerNode) {
          ((XValueContainerNode)parent).startComputingChildren();
          return true;
        }
      }
    }
    return false;
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
    if (PlatformDataKeys.PREDEFINED_TEXT.is(dataId)) {
      XValueNodeImpl[] selectedNodes = getSelectedNodes(XValueNodeImpl.class, null);
      if (selectedNodes.length == 1 && selectedNodes[0].getFullValueEvaluator() == null) {
        return DebuggerUIUtil.getNodeRawValue(selectedNodes[0]);
      }
    }
    return null;
  }

  public void rebuildAndRestore(final XDebuggerTreeState treeState) {
    Object rootNode = myTreeModel.getRoot();
    if (rootNode instanceof XDebuggerTreeNode) {
      ((XDebuggerTreeNode)rootNode).clearChildren();
      if (isRootVisible() && rootNode instanceof XValueNodeImpl) {
        ((XValueNodeImpl)rootNode).getValueContainer().computePresentation((XValueNode)rootNode, XValuePlace.TREE);
      }
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
    // clear all possible inner fields that may still have links to debugger objects
    setModel(null);
    myTreeModel.setRoot(null);
    setCellRenderer(null);
    UIUtil.dispose(this);
    setLeadSelectionPath(null);
    setAnchorSelectionPath(null);
    removeComponentListener(myMoveListener);
    removeTreeExpansionListener(myTreeExpansionListener);
    myListeners.clear();
  }

  private void registerShortcuts() {
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.SET_VALUE, this, this);
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.COPY_VALUE, this, this);
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.JUMP_TO_SOURCE, this, this);
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.JUMP_TO_TYPE_SOURCE, this, this);
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.MARK_OBJECT, this, this);
  }

  private static void markNodesObsolete(final XValueContainerNode<?> node) {
    node.setObsolete();
    node.getLoadedChildren().forEach(XDebuggerTree::markNodesObsolete);
  }

  @Nullable
  public static XDebuggerTree getTree(final AnActionEvent e) {
    return e.getData(XDEBUGGER_TREE_KEY);
  }

  @Nullable
  public static XDebuggerTree getTree(DataContext context) {
    return XDEBUGGER_TREE_KEY.getData(context);
  }

  public TransferToEDTQueue<Runnable> getLaterInvocator() {
    return myLaterInvocator;
  }

  public void selectNodeOnLoad(final Condition<TreeNode> nodeFilter) {
    addTreeListener(new XDebuggerTreeListener() {
      @Override
      public void nodeLoaded(@NotNull RestorableStateNode node, String name) {
        if (nodeFilter.value(node)) {
          setSelectionPath(node.getPath());
          removeTreeListener(this); // remove the listener on first match
        }
      }
    });
  }

  public void expandNodesOnLoad(final Condition<TreeNode> nodeFilter) {
    addTreeListener(new XDebuggerTreeListener() {
      @Override
      public void nodeLoaded(@NotNull RestorableStateNode node, String name) {
        if (nodeFilter.value(node) && !node.isLeaf()) {
          // cause children computing
          node.getChildCount();
        }
      }

      @Override
      public void childrenLoaded(@NotNull XDebuggerTreeNode node, @NotNull List<XValueContainerNode<?>> children, boolean last) {
        if (nodeFilter.value(node)) {
          expandPath(node.getPath());
        }
      }
    });
  }

  public boolean isDetached() {
    return DataManager.getInstance().getDataContext(this).getData(XDebugSessionTab.TAB_KEY) == null;
  }
}
