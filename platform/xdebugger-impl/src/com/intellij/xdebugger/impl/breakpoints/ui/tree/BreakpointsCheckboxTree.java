/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.breakpoints.ui.tree;

import com.intellij.openapi.project.Project;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;

public class BreakpointsCheckboxTree extends CheckboxTree {

  @Override
  protected void nodeStateWillChange(CheckedTreeNode node) {
    super.nodeStateWillChange(node);
    if (myDelegate != null) {
      myDelegate.nodeStateWillChange(node);
    }
  }

  @Override
  protected void onNodeStateChanged(CheckedTreeNode node) {
    super.onNodeStateChanged(node);
    if (myDelegate != null) {
      myDelegate.nodeStateDidChange(node);
    }
  }

  interface Delegate {
    void nodeStateDidChange(CheckedTreeNode node);

    void nodeStateWillChange(CheckedTreeNode node);
  }

  public void setDelegate(Delegate delegate) {
    myDelegate = delegate;
  }

  private Delegate myDelegate = null;

  public BreakpointsCheckboxTree(Project project, BreakpointItemsTreeController model) {
    super(new BreakpointsTreeCellRenderer.BreakpointsCheckboxTreeCellRenderer(project), model.getRoot());
    setHorizontalAutoScrollingEnabled(false);
  }
}
