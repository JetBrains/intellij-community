// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.impl.evaluate.XDebuggerEvaluationDialog;
import com.intellij.xdebugger.impl.evaluate.XEvaluationOrigin;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class EvaluatingExpressionRootNode extends XValueContainerNode<EvaluatingExpressionRootNode.EvaluatingResultContainer> {
  public EvaluatingExpressionRootNode(XDebuggerEvaluationDialog evaluationDialog, final XDebuggerTree tree) {
    super(tree, null, false, new EvaluatingResultContainer(evaluationDialog));
  }

  @Override
  protected MessageTreeNode createLoadingMessageNode() {
    return MessageTreeNode.createEvaluatingMessage(myTree, this);
  }

  @ApiStatus.Internal
  public void rebuild() {
    EvaluatingResultContainer valueContainer = getValueContainer();
    if (valueContainer.myResult != null) {
      XDebuggerTree tree = getTree();
      XDebuggerTreeState treeState = XDebuggerTreeState.saveState(tree);
      clearChildren();
      treeState.restoreState(tree);
    }
  }

  public static class EvaluatingResultContainer extends XValueContainer {
    private final XDebuggerEvaluationDialog myDialog;
    private XValue myResult = null;

    public EvaluatingResultContainer(final XDebuggerEvaluationDialog dialog) {
      myDialog = dialog;
    }

    @Override
    public void computeChildren(final @NotNull XCompositeNode node) {
      if (myResult != null) {
        addResult(node, myResult);
      }
      else {
        myDialog.startEvaluation(new MyEvaluationCallback(node));
      }
    }

    private static void addResult(@NotNull XCompositeNode node, @NotNull XValue result) {
      String name = UIUtil.removeMnemonic(XDebuggerBundle.message("xdebugger.evaluate.result"));
      node.addChildren(XValueChildrenList.singleton(name, result), true);
    }

    private class MyEvaluationCallback extends XEvaluationCallbackBase implements XEvaluationCallbackWithOrigin {
      private final @NotNull XCompositeNode myNode;

      private MyEvaluationCallback(@NotNull XCompositeNode node) { myNode = node; }

      @Override
      public XEvaluationOrigin getOrigin() {
        return XEvaluationOrigin.DIALOG;
      }

      @Override
      public void evaluated(final @NotNull XValue result) {
        myResult = result;
        addResult(myNode, result);
        myDialog.evaluationDone();
      }

      @Override
      public void errorOccurred(final @NotNull String errorMessage) {
        myNode.setErrorMessage(errorMessage);
        myDialog.evaluationDone();
      }
    }
  }
}
