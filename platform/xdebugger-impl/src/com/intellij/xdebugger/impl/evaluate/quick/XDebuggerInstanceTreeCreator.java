// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate.quick;

import com.intellij.concurrency.ResultConsumer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.evaluation.XInstanceEvaluator;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.evaluate.quick.common.DebuggerTreeCreator;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeNode;

@ApiStatus.Internal
public class XDebuggerInstanceTreeCreator implements DebuggerTreeCreator<Pair<XInstanceEvaluator,String>> {
  private final @NotNull Project myProject;
  private final XDebuggerEditorsProvider myProvider;
  private final XSourcePosition myPosition;
  private final XValueMarkers<?, ?> myMarkers;
  private final XDebugSessionProxy mySession;

  public XDebuggerInstanceTreeCreator(@NotNull Project project, XDebuggerEditorsProvider editorsProvider, XSourcePosition sourcePosition,
                                      XValueMarkers<?, ?> markers, XDebugSessionProxy session) {
    myProject = project;
    myProvider = editorsProvider;
    myPosition = sourcePosition;
    myMarkers = markers;
    mySession = session;
  }

  @Override
  public @NotNull Tree createTree(@NotNull Pair<XInstanceEvaluator, String> descriptor) {
    final XDebuggerTree tree = new XDebuggerTree(myProject, myProvider, myPosition, XDebuggerActions.INSPECT_TREE_POPUP_GROUP, myMarkers);
    final XValueNodeImpl root = new XValueNodeImpl(tree, null, descriptor.getSecond(),
                                                   new InstanceEvaluatorTreeRootValue(descriptor.getFirst(), descriptor.getSecond()));
    tree.setRoot(root, false);
    Condition<TreeNode> visibleRootCondition = node -> node.getParent() == root;
    tree.expandNodesOnLoad(visibleRootCondition);
    tree.selectNodeOnLoad(visibleRootCondition, Conditions.alwaysFalse());

    return tree;
  }

  @Override
  public @NotNull String getTitle(@NotNull Pair<XInstanceEvaluator, String> descriptor) {
    return descriptor.getSecond();
  }

  @Override
  public void createDescriptorByNode(Object node, ResultConsumer<? super Pair<XInstanceEvaluator, String>> resultConsumer) {
    if (node instanceof XValueNodeImpl valueNode) {
      resultConsumer.onSuccess(Pair.create(valueNode.getValueContainer().getInstanceEvaluator(), valueNode.getName()));
    }
  }

  private class InstanceEvaluatorTreeRootValue extends XValue {
    private final XInstanceEvaluator myInstanceEvaluator;
    private final String myName;

    InstanceEvaluatorTreeRootValue(XInstanceEvaluator instanceEvaluator, String name) {
      myInstanceEvaluator = instanceEvaluator;
      myName = name;
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
      node.setPresentation(null, null, "root", true);
    }

    @Override
    public void computeChildren(final @NotNull XCompositeNode node) {
      XStackFrame frame = mySession.getCurrentStackFrame();
      if (frame != null) {
        myInstanceEvaluator.evaluate(new XDebuggerEvaluator.XEvaluationCallback() {
          @Override
          public void evaluated(@NotNull XValue result) {
            node.addChildren(XValueChildrenList.singleton(myName, result), true);
          }

          @Override
          public void errorOccurred(@NotNull String errorMessage) {

          }
        }, frame);
      }
      else {
        node.setErrorMessage("Frame is not available");
      }
    }
  }
}