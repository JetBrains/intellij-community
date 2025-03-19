// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Internal
public class DebuggerTreeWithHistoryPanel<D> extends DebuggerTreeWithHistoryContainer<D> {
  private final BorderLayoutPanel myMainPanel;
  private final Disposable myDisposable;
  private XDebuggerTree myTree;

  public DebuggerTreeWithHistoryPanel(@NotNull D initialItem, @NotNull DebuggerTreeCreator<D> creator, @NotNull Project project, Disposable disposable) {
    super(initialItem, creator, project);
    myDisposable = disposable;
    myTree = (XDebuggerTree)myTreeCreator.createTree(initialItem);
    registerTreeDisposable(myDisposable, myTree);
    myMainPanel = createMainPanel(myTree);
  }

  @Override
  protected void updateContainer(Tree tree, String title) {
    myTree = (XDebuggerTree)tree;
    registerTreeDisposable(myDisposable, tree);
    myMainPanel.removeAll();
    fillMainPanel(myMainPanel, tree);
    myMainPanel.revalidate();
    myMainPanel.repaint();
  }

  @Override
  protected BorderLayoutPanel fillMainPanel(BorderLayoutPanel mainPanel, Tree tree) {
    return mainPanel.addToCenter(ScrollPaneFactory.createScrollPane(tree)).addToTop(createToolbar(mainPanel, tree));
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  public void rebuild() {
    myTree.invokeLater(() -> myTree.rebuildAndRestore(XDebuggerTreeState.saveState(myTree)));
  }

  public @NotNull XDebuggerTree getTree() {
    return myTree;
  }
}
