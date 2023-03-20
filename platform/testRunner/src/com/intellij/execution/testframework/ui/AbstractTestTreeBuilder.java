// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * @deprecated use {@link AbstractTestTreeBuilderBase} directly
 * @see com.intellij.execution.testframework.sm.runner.SMTRunnerTreeBuilder
 */
@Deprecated(forRemoval = true)
public abstract class AbstractTestTreeBuilder extends AbstractTreeBuilder implements AbstractTestTreeBuilderBase<AbstractTestProxy> {
  public AbstractTestTreeBuilder(final JTree tree,
                                 final DefaultTreeModel defaultTreeModel,
                                 final AbstractTreeStructure structure,
                                 final IndexComparator instance) {
    super(tree, defaultTreeModel, structure, instance);
  }

  public AbstractTestTreeBuilder() {
    super();
  }

  @Override
  public void repaintWithParents(@NotNull AbstractTestProxy testProxy) {
    AbstractTestProxy current = testProxy;
    do {
      DefaultMutableTreeNode node = getNodeForElement(current);
      if (node != null) {
        JTree tree = getTree();
        ((DefaultTreeModel)tree.getModel()).nodeChanged(node);
      }
      current = current.getParent();
    }
    while (current != null);
  }

  @Override
  @NotNull
  protected ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
  }

  @Override
  protected boolean isSmartExpand() {
    return false;
  }

  @Override
  public void setTestsComparator(TestFrameworkRunningModel model) {
    setNodeDescriptorComparator(model.createComparator());
    queueUpdate();
  }
}
