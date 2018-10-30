// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.ui.AbstractTestTreeBuilderBase;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.util.Alarm;

import javax.swing.*;

public class SMTRunnerTreeBuilder implements Disposable, AbstractTestTreeBuilderBase<SMTestProxy> {
  private final JTree myTree;
  private final SMTRunnerTreeStructure myTreeStructure;
  private boolean myDisposed;
  private StructureTreeModel myTreeModel;
  private final Alarm mySelectionAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);

  public SMTRunnerTreeBuilder(final JTree tree, final SMTRunnerTreeStructure structure) {
    myTree = tree;
    myTreeStructure = structure;
  }

  @Override
  public void repaintWithParents(SMTestProxy testProxy) {
    do {
      getTreeModel().invalidate(testProxy, false);
      testProxy = testProxy.getParent();
    }
    while (testProxy != null);
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }

  public boolean isDisposed() {
    return myDisposed;
  }


  public void updateTestsSubtree(final SMTestProxy parentTestProxy) {
    getTreeModel().invalidate(parentTestProxy, true);
  }
  
  public JTree getTree() {
    return myTree;
  }

  public SMTRunnerTreeStructure getTreeStructure() {
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
    getTreeModel().expand(test, getTree(), path -> {
    });
  }

  public void select(Object proxy, Runnable onDone) {
    if (!(proxy instanceof AbstractTestProxy)) return;
    mySelectionAlarm.cancelAllRequests();
    mySelectionAlarm.addRequest(() -> getTreeModel().select(proxy, getTree(), path -> {
      if (onDone != null) onDone.run();
    }), 50);
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
