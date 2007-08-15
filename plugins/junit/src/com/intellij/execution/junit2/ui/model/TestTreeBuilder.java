package com.intellij.execution.junit2.ui.model;

import com.intellij.execution.junit2.TestEvent;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestTreeView;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class TestTreeBuilder extends AbstractTreeBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.ui.model.TestTreeBuilder");

  private JUnitRunningModel myModel;
  private final JUnitAdapter myListener = new JUnitAdapter(){
    private final Collection<TestProxy> myNodesToUpdate = new HashSet<TestProxy>();
    public void onEventsDispatched(final List<TestEvent> events) {
      for (final TestEvent event : events) {
        final TestProxy testSubtree = (TestProxy)event.getTestSubtree();
        if (testSubtree != null) myNodesToUpdate.add(testSubtree);
      }
      updateTree();
    }

    public void doDispose() {
      myModel = null;
      myNodesToUpdate.clear();
    }

    private void updateTree() {
      TestProxy parentToUpdate = null;
      for (final TestProxy test : myNodesToUpdate) {
        parentToUpdate = test.getCommonAncestor(parentToUpdate);
        if (parentToUpdate.getParent() == null) break;
      }
      final DefaultMutableTreeNode node = getNodeForElement(parentToUpdate);
      if (node != null) {
        updateSubtree(node);
        myNodesToUpdate.clear();
     }
    }
  };

  public TestTreeBuilder(final TestTreeView tree, final JUnitRunningModel model, final JUnitConsoleProperties properties) {
    this(tree, new TestTreeStructure(model.getRoot(), properties), model);
  }

  private TestTreeBuilder(final JTree tree, final TestTreeStructure treeStructure, final JUnitRunningModel model) {
    this(tree, new DefaultTreeModel(new DefaultMutableTreeNode(treeStructure.createDescriptor(model.getRoot(), null))), treeStructure);
    treeStructure.setSpecialNode(new SpecialNode(this, model));
    myModel = model;
    myModel.addListener(myListener);
    initRootNode();
  }

  private TestTreeBuilder(final JTree tree, final DefaultTreeModel treeModel, final AbstractTreeStructure treeStructure) {
    super(tree,
          treeModel,
          treeStructure,
          IndexComparator.INSTANCE);
    tree.setModel(treeModel);
  }

  protected boolean isSmartExpand() {
    return false;
  }

  protected boolean isAlwaysShowPlus(final NodeDescriptor nodeDescriptor) {
    return false;
  }

  protected boolean isAutoExpandNode(final NodeDescriptor nodeDescriptor) {
    return nodeDescriptor.getElement() == myModel.getRoot();
  }

  public void repaintWithParents(final TestProxy child) {
    TestProxy test = child;
    do {
      final DefaultMutableTreeNode node = getNodeForElement(test);
      if (node != null) {
        final JTree tree = getTree();
        ((DefaultTreeModel) tree.getModel()).nodeChanged(node);
      }
      test = test.getParent();
    } while (test != null);
  }

  @Nullable
  public DefaultMutableTreeNode ensureTestVisible(final TestProxy test) {
    DefaultMutableTreeNode node = getNodeForElement(test);
    if (node != null) {
      if (node.getParent() != null) {
        expandNodeChildren((DefaultMutableTreeNode) node.getParent());
        node = getNodeForElement(test);
      }
      return node;
    }
    final AbstractTestProxy[] parents = test.getPathFromRoot();

    for (final AbstractTestProxy parent : parents) {
      buildNodeForElement(parent);
      node = getNodeForElement(parent);
      if (node != null) {
        expandNodeChildren(node);
      }
    }
    return node;
  }

  @NotNull
  protected ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
  }
}
