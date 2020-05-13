// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDSource;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public final class XDebuggerTreePanel implements DnDSource {
  private final XDebuggerTree myTree;
  private final JPanel myMainPanel;
  private Component myHeaderComponent;
  private XDebuggerTreeSearchSession mySearchSession;

  public XDebuggerTreePanel(final @NotNull Project project, final @NotNull XDebuggerEditorsProvider editorsProvider,
                            @NotNull Disposable parentDisposable, final @Nullable XSourcePosition sourcePosition,
                            @NotNull @NonNls final String popupActionGroupId, @Nullable XValueMarkers<?, ?> markers) {
    myTree = new XDebuggerTree(project, editorsProvider, sourcePosition, popupActionGroupId, markers);
    myMainPanel = new JPanel(new BorderLayout());
    myMainPanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    Disposer.register(parentDisposable, myTree);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        myMainPanel.removeAll();
      }
    });

    if (popupActionGroupId.equals(XDebuggerActions.WATCHES_TREE_POPUP_GROUP)
        || popupActionGroupId.equals(XDebuggerActions.VARIABLES_TREE_POPUP_GROUP)
    ) {
      SearchAction searchAction = new SearchAction();
      searchAction.registerCustomShortcutSet(myMainPanel, myTree);
    }
  }

  @NotNull
  public XDebuggerTree getTree() {
    return myTree;
  }

  @NotNull
  public JPanel getMainPanel() {
    return myMainPanel;
  }


  void setHeaderComponent(Component header) {
    if (myHeaderComponent != null) {
      myMainPanel.remove(myHeaderComponent);
    }
    if (header != null) {
      myMainPanel.add(header, BorderLayout.NORTH);
    }
    myMainPanel.revalidate();
    myMainPanel.repaint();
    myHeaderComponent = header;
  }

  @Nullable
  Component getHeaderComponent() {
    return myHeaderComponent;
  }

  void searchSessionStopped() {
    mySearchSession = null;
  }

  class SearchAction extends AnAction {

    SearchAction() {
      ActionUtil.copyFrom(this, IdeActions.ACTION_FIND);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      if (mySearchSession == null) {
        mySearchSession = new XDebuggerTreeSearchSession(XDebuggerTreePanel.this, myTree.getProject());
      }
    }
  }


  @Override
  public boolean canStartDragging(final DnDAction action, final Point dragOrigin) {
    return getNodesToDrag().length > 0;
  }

  private XValueNodeImpl[] getNodesToDrag() {
    return myTree.getSelectedNodes(XValueNodeImpl.class, node -> DebuggerUIUtil.hasEvaluationExpression(node.getValueContainer()));
  }

  @Override
  public DnDDragStartBean startDragging(final DnDAction action, final Point dragOrigin) {
    return new DnDDragStartBean(getNodesToDrag());
  }

  @Override
  public Pair<Image, Point> createDraggedImage(final DnDAction action, final Point dragOrigin) {
    XValueNodeImpl[] nodes = getNodesToDrag();
    if (nodes.length == 1) {
      return DnDAwareTree.getDragImage(myTree, nodes[0].getPath(), dragOrigin);
    }
    return DnDAwareTree.getDragImage(myTree, XDebuggerBundle.message("xdebugger.drag.text.0.elements", nodes.length), dragOrigin);
  }
}
