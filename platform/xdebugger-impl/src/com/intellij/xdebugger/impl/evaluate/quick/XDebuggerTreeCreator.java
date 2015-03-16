/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.evaluate.quick;

import com.intellij.concurrency.ResultConsumer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.evaluate.quick.common.DebuggerTreeCreator;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeNode;

public class XDebuggerTreeCreator implements DebuggerTreeCreator<Pair<XValue,String>> {
  @NotNull private final Project myProject;
  private final XDebuggerEditorsProvider myProvider;
  private final XSourcePosition myPosition;
  private final XValueMarkers<?, ?> myMarkers;

  public XDebuggerTreeCreator(@NotNull Project project, XDebuggerEditorsProvider editorsProvider, XSourcePosition sourcePosition,
                              XValueMarkers<?, ?> markers) {
    myProject = project;
    myProvider = editorsProvider;
    myPosition = sourcePosition;
    myMarkers = markers;
  }

  @NotNull
  @Override
  public Tree createTree(@NotNull Pair<XValue, String> descriptor) {
    final XDebuggerTree tree = new XDebuggerTree(myProject, myProvider, myPosition, XDebuggerActions.INSPECT_TREE_POPUP_GROUP, myMarkers);
    final XValueNodeImpl root = new XValueNodeImpl(tree, null, descriptor.getSecond(), descriptor.getFirst());
    tree.setRoot(root, true);
    tree.setSelectionRow(0);
    // expand root on load
    tree.expandNodesOnLoad(new Condition<TreeNode>() {
      @Override
      public boolean value(TreeNode node) {
        return node == root;
      }
    });
    return tree;
  }

  @NotNull
  @Override
  public String getTitle(@NotNull Pair<XValue, String> descriptor) {
    return descriptor.getSecond();
  }

  @Override
  public void createDescriptorByNode(Object node, ResultConsumer<Pair<XValue, String>> resultConsumer) {
    if (node instanceof XValueNodeImpl) {
      XValueNodeImpl valueNode = (XValueNodeImpl)node;
      resultConsumer.onSuccess(Pair.create(valueNode.getValueContainer(), valueNode.getName()));
    }
  }
}