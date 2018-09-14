// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.ui.AbstractTestTreeBuilderBase;
import com.intellij.execution.testframework.ui.BaseTestProxyNodeDescriptor;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

public class SMTRunnerTreeBuilder implements Disposable, AbstractTestTreeBuilderBase {
  private final JTree myTree;
  private final AbstractTreeStructure myTreeStructure;
  private boolean myDisposed;
  private StructureTreeModel myTreeModel;

  public SMTRunnerTreeBuilder(final JTree tree, final SMTRunnerTreeStructure structure) {
    myTree = tree;
    myTreeStructure = structure;
  }

  @Override
  public void repaintWithParents(final AbstractTestProxy testProxy) {
    TreeUtil.promiseVisit(getTree(), visitor(testProxy))
      .onSuccess(path -> {
        if (path != null) {
          myTreeModel.invalidate(path, true);
        }
        else {
          myTreeModel.invalidate();
        }
      });
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  
  public SMTRunnerTreeStructure getSMRunnerTreeStructure() {
    return ((SMTRunnerTreeStructure)getTreeStructure());
  }

  public void updateTestsSubtree(final SMTestProxy parentTestProxy) {
    TreeUtil.promiseVisit(getTree(), visitor(parentTestProxy))
      .onSuccess(path -> {
        if (path != null) {
          getTreeModel().invalidate(path, true);
        }
        else {
          getTreeModel().invalidate();
        }
      });
  }
  
  public JTree getTree() {
    return myTree;
  }

  public AbstractTreeStructure getTreeStructure() {
    return myTreeStructure;
  }

  protected StructureTreeModel getTreeModel() {
    return myTreeModel;
  }

  //todo
  protected boolean isSmartExpand() {
    return false;
  }

  @Override
  public void setTestsComparator(TestFrameworkRunningModel model) {
    myTreeModel.setComparator(model.createComparator());
  }

  public void updateFromRoot() {
    myTreeModel.invalidate();
  }

  public void setModel(StructureTreeModel asyncTreeModel) {
    myTreeModel = asyncTreeModel;
  }


  public void performUpdate() {
    getTreeModel().invalidate();
  }

  public void expand(AbstractTestProxy test) {
    TreeUtil.promiseExpand(getTree(), visitor(test));
  }

  public void select(Object proxy, Runnable onDone) {
    if (!(proxy instanceof AbstractTestProxy)) return;
    TreeUtil.promiseSelect(getTree(), visitor((AbstractTestProxy)proxy))
      .onSuccess(path -> {
        if (onDone != null) {
          onDone.run();
        }
      });
  }

  @NotNull
  private static TreeVisitor visitor(@NotNull AbstractTestProxy proxy) {
    return path -> {
      Object component = path.getLastPathComponent();
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)component;
      Object object = node.getUserObject();
      AbstractTestProxy currentProxy = ((BaseTestProxyNodeDescriptor)object).getElement();
      if (currentProxy == proxy) return TreeVisitor.Action.INTERRUPT;
      return isAncestor(currentProxy, proxy)
             ? TreeVisitor.Action.CONTINUE
             : TreeVisitor.Action.SKIP_CHILDREN;
    };
  }

  private static boolean isAncestor(AbstractTestProxy parentProxy, AbstractTestProxy proxy) {
    if (parentProxy.isLeaf()) return false;

    AbstractTestProxy parent = proxy.getParent();
    while (parent != null) {
      if (parent == parentProxy) return true;
      parent = parent.getParent();
    }
    return false;
  }

  //todo
  protected boolean isAutoExpandNode(final NodeDescriptor nodeDescriptor) {
    final AbstractTreeStructure treeStructure = getTreeStructure();
    final Object rootElement = treeStructure.getRootElement();
    final Object nodeElement = nodeDescriptor.getElement();

    if (nodeElement == rootElement) {
      return true;
    }

    if (((SMTestProxy)nodeElement).getParent() == rootElement
        && ((SMTestProxy)rootElement).getChildren().size() == 1) {
      return true;
    }
    return false;
  }
}
