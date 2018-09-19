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
import com.intellij.util.Alarm;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class SMTRunnerTreeBuilder implements Disposable, AbstractTestTreeBuilderBase<SMTestProxy> {
  private final JTree myTree;
  private final AbstractTreeStructure myTreeStructure;
  private boolean myDisposed;
  private StructureTreeModel myTreeModel;
  private final Alarm mySelectionAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);

  public SMTRunnerTreeBuilder(final JTree tree, final SMTRunnerTreeStructure structure) {
    myTree = tree;
    myTreeStructure = structure;
  }

  @Override
  public void repaintWithParents(final SMTestProxy testProxy) {
    updateTestsSubtree(testProxy);
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

  public void expand(AbstractTestProxy test) {
    TreeUtil.promiseExpand(getTree(), visitor(test));
  }

  public void select(Object proxy, Runnable onDone) {
    if (!(proxy instanceof AbstractTestProxy)) return;
    mySelectionAlarm.cancelAllRequests();
    mySelectionAlarm.addRequest(() -> TreeUtil.promiseSelect(getTree(), visitor((AbstractTestProxy)proxy))
      .onSuccess(path -> {
        if (onDone != null) {
          onDone.run();
        }
      }), 50);
  }

  @NotNull
  private static TreeVisitor visitor(@NotNull AbstractTestProxy proxy) {
    return path -> {
      BaseTestProxyNodeDescriptor descriptor = TreeUtil.getLastUserObject(BaseTestProxyNodeDescriptor.class, path);;
      assert descriptor != null;
      AbstractTestProxy currentProxy = descriptor.getElement();
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
