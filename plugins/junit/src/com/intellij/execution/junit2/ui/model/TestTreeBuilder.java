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

package com.intellij.execution.junit2.ui.model;

import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.events.TestEvent;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.TestTreeView;
import com.intellij.execution.testframework.ui.AbstractTestTreeBuilder;
import com.intellij.ide.util.treeView.IndexComparator;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class TestTreeBuilder extends AbstractTestTreeBuilder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.execution.junit2.ui.model.TestTreeBuilder");

  private JUnitRunningModel myModel;
  private final JUnitAdapter myListener = new JUnitAdapter() {
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
      getUi().queueUpdate(parentToUpdate);
      myNodesToUpdate.clear();
    }
  };

  public TestTreeBuilder(final TestTreeView tree, final JUnitRunningModel model, final JUnitConsoleProperties properties) {
    this(tree, new TestTreeStructure(model.getRoot(), properties), model);
  }

  private TestTreeBuilder(final JTree tree, final TestTreeStructure treeStructure, final JUnitRunningModel model) {
    treeStructure.setSpecialNode(new SpecialNode(this, model));
    myModel = model;
    myModel.addListener(myListener);
    init(tree, new DefaultTreeModel(new DefaultMutableTreeNode(treeStructure.createDescriptor(model.getRoot(), null))), treeStructure,
         IndexComparator.INSTANCE, true);
    initRootNode();
  }

  protected boolean isAutoExpandNode(final NodeDescriptor nodeDescriptor) {
    return nodeDescriptor.getElement() == myModel.getRoot();
  }

  @Nullable
  public DefaultMutableTreeNode ensureTestVisible(final TestProxy test) {
    DefaultMutableTreeNode node = getNodeForElement(test);
    if (node != null) {
      if (node.getParent() != null) {
        expandNodeChildren((DefaultMutableTreeNode)node.getParent());
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
}
