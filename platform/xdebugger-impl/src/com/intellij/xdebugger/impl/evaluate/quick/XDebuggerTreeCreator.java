// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate.quick;

import com.intellij.concurrency.ResultConsumer;
import com.intellij.openapi.project.Project;
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

public class XDebuggerTreeCreator implements DebuggerTreeCreator<Pair<XValue,String>> {
  private final @NotNull Project myProject;
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

  @Override
  public @NotNull Tree createTree(@NotNull Pair<XValue, String> descriptor) {
    final XDebuggerTree tree = new XDebuggerTree(myProject, myProvider, myPosition, XDebuggerActions.INSPECT_TREE_POPUP_GROUP, myMarkers);
    final XValueNodeImpl root = new XValueNodeImpl(tree, null, descriptor.getSecond(), descriptor.getFirst());
    tree.setRoot(root, true);
    tree.setSelectionRow(0);
    // expand root on load
    tree.expandNodesOnLoad(node -> node == root);
    return tree;
  }

  @Override
  public @NotNull String getTitle(@NotNull Pair<XValue, String> descriptor) {
    return descriptor.getSecond();
  }

  @Override
  public void createDescriptorByNode(Object node, ResultConsumer<? super Pair<XValue, String>> resultConsumer) {
    if (node instanceof XValueNodeImpl valueNode) {
      resultConsumer.onSuccess(Pair.create(valueNode.getValueContainer(), valueNode.getName()));
    }
  }
}