// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.execution.configurations.RemoteRunProfile;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.DataManager;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.issueLinks.TreeLinkMouseListener;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.SingleAlarm;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.TextTransferable;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.pinned.items.XDebuggerPinToTopManager;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.impl.ui.tree.nodes.*;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.util.List;
import java.util.function.Function;

public class XDebuggerTree extends DnDAwareTree implements UiCompatibleDataProvider, Disposable {
  private final ComponentListener myMoveListener = new ComponentAdapter() {
    @Override
    public void componentMoved(ComponentEvent e) {
      repaint(); // needed to repaint links in cell renderer on horizontal scrolling
    }
  };

  public static final DataKey<XDebuggerTree> XDEBUGGER_TREE_KEY = DataKey.create("xdebugger.tree");
  public static final DataKey<List<XValueNodeImpl>> SELECTED_NODES = DataKey.create("xdebugger.selected.nodes");

  private final SingleAlarm myAlarm = new SingleAlarm(new Runnable() {
    @Override
    public void run() {
      DebuggerUIUtil.repaintCurrentEditor(myProject);
    }
  }, 100, this);

  private static final Function<TreePath, String> SPEED_SEARCH_CONVERTER = o -> {
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
      if (!(c instanceof XDebuggerTree tree)) {
        return null;
      }
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
      return new TextTransferable(htmlBuf, plainBuf);
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
  private final XDebuggerPinToTopManager myPinToTopManager;
  private XDebuggerTreeRestorer myCurrentRestorer;
  private @Nullable TreeSpeedSearch myTreeSpeedSearch;

  public XDebuggerTree(final @NotNull Project project,
                       final @NotNull XDebuggerEditorsProvider editorsProvider,
                       final @Nullable XSourcePosition sourcePosition,
                       final @NotNull String popupActionGroupId, @Nullable XValueMarkers<?, ?> valueMarkers) {
    super(new DefaultTreeModel(null));
    myValueMarkers = valueMarkers;
    myProject = project;
    myEditorsProvider = editorsProvider;
    mySourcePosition = sourcePosition;
    myTreeModel = (DefaultTreeModel)getModel();
    myPinToTopManager = XDebuggerPinToTopManager.Companion.getInstance(project);
    setCellRenderer(new XDebuggerTreeRenderer(myProject));
    new TreeLinkMouseListener(new XDebuggerTreeRenderer(myProject)) {
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

      @Override
      public void mouseMoved(MouseEvent e) {
        super.mouseMoved(e);
        if (!myPinToTopManager.isEnabled()) {
          return;
        }
        TreePath pathForLocation = getPathForLocation(e.getX(), e.getY());
        if (pathForLocation == null) {
          myPinToTopManager.onNodeHovered(null, XDebuggerTree.this);
          return;
        }
        Object lastPathComponent = pathForLocation.getLastPathComponent();
        myPinToTopManager
          .onNodeHovered(lastPathComponent instanceof XDebuggerTreeNode ? (XDebuggerTreeNode) lastPathComponent : null, XDebuggerTree.this);
      }
    }.installOn(this);
    setRootVisible(false);
    setShowsRootHandles(true);

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        return expandIfEllipsis(e);
      }
    }.installOn(this);

    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_ENTER || key == KeyEvent.VK_SPACE || key == KeyEvent.VK_RIGHT) {
          expandIfEllipsis(dummyMouseClickEvent());
        }
      }
    });

    installSpeedSearch();
    PopupHandler.installPopupMenu(this, popupActionGroupId, "XDebuggerTreePopup");
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
                link.onClick(dummyMouseClickEvent());
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

      private static void handleExpansion(TreeExpansionEvent event, boolean expanded) {
        final TreePath path = event.getPath();
        final Object component = path != null ? path.getLastPathComponent() : null;
        if (component instanceof XValueGroupNodeImpl) {
          ((XValueGroupNodeImpl)component).onExpansion(expanded);
        }
      }
    };
    addTreeExpansionListener(myTreeExpansionListener);
  }

  /**
   * Called to find an element with this name in the tree and request its focus.
   */
  @ApiStatus.Internal
  public void findElementAndRequestFocus(String searchQuery) {
    if (myTreeSpeedSearch == null) return;
    myTreeSpeedSearch.findAndSelectElement(searchQuery);
  }

  /**
   * Called from the tree constructor during initialization. Override if the speed search is not required for a derived class.
   */
  protected void installSpeedSearch() {
    if (Registry.is("debugger.variablesView.rss")) {
      myTreeSpeedSearch = XDebuggerTreeSpeedSearch.installOn(this, SPEED_SEARCH_CONVERTER);
    }
    else {
      myTreeSpeedSearch = TreeSpeedSearch.installOn(this, false, SPEED_SEARCH_CONVERTER);
    }
  }

  private @NotNull MouseEvent dummyMouseClickEvent() {
    return new MouseEvent(this, 0, 0, 0, 0, 0, 1, false);
  }

  public void updateEditor() {
    myAlarm.cancelAndRequest();
  }

  public boolean isUnderRemoteDebug() {
    DataContext context = DataManager.getInstance().getDataContext(this);
    ExecutionEnvironment env = ExecutionDataKeys.EXECUTION_ENVIRONMENT.getData(context);
    if (env != null && env.getRunProfile() instanceof RemoteRunProfile) {
      return true;
    }
    return false;
  }

  private boolean expandIfEllipsis(@NotNull MouseEvent e) {
    MessageTreeNode[] treeNodes = getSelectedNodes(MessageTreeNode.class, null);
    if (treeNodes.length == 1) {
      MessageTreeNode node = treeNodes[0];
      if (node.isEllipsis()) {
        XDebuggerTreeNodeHyperlink link = node.getLink();
        if (link != null) {
          link.onClick(e);
          return true;
        }
      }
    }
    return false;
  }

  public void addTreeListener(@NotNull XDebuggerTreeListener listener) {
    myListeners.add(listener);
  }

  public void addTreeListener(@NotNull XDebuggerTreeListener listener, @NotNull Disposable parentDisposable) {
    addTreeListener(listener);
    Disposer.register(parentDisposable, () -> removeTreeListener(listener));
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

  public @Nullable XSourcePosition getSourcePosition() {
    return mySourcePosition;
  }

  public void setSourcePosition(final @Nullable XSourcePosition sourcePosition) {
    mySourcePosition = sourcePosition;
  }

  public @NotNull XDebuggerEditorsProvider getEditorsProvider() {
    return myEditorsProvider;
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public @Nullable XValueMarkers<?, ?> getValueMarkers() {
    return myValueMarkers;
  }

  @ApiStatus.Internal
  public @NotNull XDebuggerPinToTopManager getPinToTopManager() {
    return myPinToTopManager;
  }

  public DefaultTreeModel getTreeModel() {
    return myTreeModel;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    XValueNodeImpl[] selection = getSelectedNodes(XValueNodeImpl.class, null);
    sink.set(XDEBUGGER_TREE_KEY, this);
    sink.set(SELECTED_NODES, List.of(selection));
    if (selection.length == 1 && selection[0].getFullValueEvaluator() == null) {
      sink.set(PlatformDataKeys.PREDEFINED_TEXT, DebuggerUIUtil.getNodeRawValue(selection[0]));
    }
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
                             final @NotNull List<? extends XValueContainerNode<?>> children,
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
      // avoid SOE
      StreamEx.<XValueContainerNode<?>>ofTree((XValueContainerNode<?>)root, n -> StreamEx.of(n.getLoadedChildren()))
        .forEach(XValueContainerNode::setObsolete);
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
    accessibleContext = null;
    removeComponentListener(myMoveListener);
    removeTreeExpansionListener(myTreeExpansionListener);
    myListeners.clear();
    disposeRestorer();
  }

  void setCurrentRestorer(@NotNull XDebuggerTreeRestorer restorer) {
    disposeRestorer();
    myCurrentRestorer = restorer;
  }

  private void disposeRestorer() {
    if (myCurrentRestorer != null) {
      myCurrentRestorer.dispose();
      myCurrentRestorer = null;
    }
  }

  private void registerShortcuts() {
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.SET_VALUE, this, this);
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.COPY_VALUE, this, this);
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.JUMP_TO_SOURCE, this, this);
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.JUMP_TO_TYPE_SOURCE, this, this);
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.MARK_OBJECT, this, this);
    DebuggerUIUtil.registerActionOnComponent(XDebuggerActions.EVALUATE_EXPRESSION, this, this);
  }

  public static @Nullable XDebuggerTree getTree(final AnActionEvent e) {
    return e.getData(XDEBUGGER_TREE_KEY);
  }

  public static @Nullable XDebuggerTree getTree(@NotNull DataContext context) {
    return XDEBUGGER_TREE_KEY.getData(context);
  }

  public static @NotNull List<XValueNodeImpl> getSelectedNodes(@NotNull DataContext context) {
    return ContainerUtil.notNullize(SELECTED_NODES.getData(context));
  }

  public void invokeLater(Runnable runnable) {
    EdtExecutorService.getInstance().execute(runnable);
  }

  public void selectNodeOnLoad(Condition<? super TreeNode> nodeFilter, Condition<? super TreeNode> obsoleteChecker) {
    addTreeListener(new XDebuggerTreeListener() {
      @Override
      public void nodeLoaded(@NotNull RestorableStateNode node, @NotNull String name) {
        if (obsoleteChecker.value(node)) {
          removeTreeListener(this);
        }
        if (nodeFilter.value(node)) {
          setSelectionPath(node.getPath());
          removeTreeListener(this); // remove the listener on first match
        }
      }
    });
  }

  public void expandNodesOnLoad(final Condition<? super TreeNode> nodeFilter) {
    addTreeListener(new XDebuggerTreeListener() {
      @Override
      public void nodeLoaded(@NotNull RestorableStateNode node, @NotNull String name) {
        if (nodeFilter.value(node) && !node.isLeaf()) {
          // cause children computing
          node.getChildCount();
        }
      }

      @Override
      public void childrenLoaded(@NotNull XDebuggerTreeNode node, @NotNull List<? extends XValueContainerNode<?>> children, boolean last) {
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
