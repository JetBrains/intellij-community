// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.impl.evaluate.XDebuggerEvaluationDialog;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;

public class EvaluatingExpressionRootNode extends XValueContainerNode<EvaluatingExpressionRootNode.EvaluatingResultContainer> {
  public EvaluatingExpressionRootNode(XDebuggerEvaluationDialog evaluationDialog, final XDebuggerTree tree) {
    super(tree, null, false, new EvaluatingResultContainer(evaluationDialog));
  }

  @Override
  protected MessageTreeNode createLoadingMessageNode() {
    return MessageTreeNode.createEvaluatingMessage(myTree, this);
  }

  public static class EvaluatingResultContainer extends XValueContainer {
    private final XDebuggerEvaluationDialog myDialog;

    public EvaluatingResultContainer(final XDebuggerEvaluationDialog dialog) {
      myDialog = dialog;
    }

    @Override
    public void computeChildren(final @NotNull XCompositeNode node) {
      myDialog.startEvaluation(new MyEvaluationCallback(node));
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
        String name = UIUtil.removeMnemonic(XDebuggerBundle.message("xdebugger.evaluate.result"));
        myNode.addChildren(XValueChildrenList.singleton(name, result), true);
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
