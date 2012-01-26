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
package com.intellij.execution.testframework.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.ide.util.treeView.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.StatusBarProgress;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

/**
 * @author: Roman Chernyatchik
 */
public abstract class AbstractTestTreeBuilder extends AbstractTreeBuilder {
  public AbstractTestTreeBuilder(final JTree tree,
                                 final DefaultTreeModel defaultTreeModel,
                                 final AbstractTreeStructure structure,
                                 final IndexComparator instance) {
    super(tree, defaultTreeModel, structure, instance);
  }

  public AbstractTestTreeBuilder() {
    super();
  }

  public void repaintWithParents(final AbstractTestProxy testProxy) {
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

  protected boolean isAlwaysShowPlus(final NodeDescriptor descriptor) {
    return false;
  }

  @NotNull
  protected ProgressIndicator createProgressIndicator() {
    return new StatusBarProgress();
  }

  protected boolean isSmartExpand() {
    return false;
  }

  public void setTestsComparator(boolean sortAlphabetically) {
    setNodeDescriptorComparator(sortAlphabetically ? AlphaComparator.INSTANCE : null);
    queueUpdate();
  }
}
