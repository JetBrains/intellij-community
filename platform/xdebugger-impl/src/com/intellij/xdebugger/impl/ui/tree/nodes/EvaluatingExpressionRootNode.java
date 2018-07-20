// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

/**
 * @author nik
 */
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
    public void computeChildren(@NotNull final XCompositeNode node) {
      myDialog.startEvaluation(new XEvaluationCallbackBase() {
        @Override
        public void evaluated(@NotNull final XValue result) {
          String name = UIUtil.removeMnemonic(XDebuggerBundle.message("xdebugger.evaluate.result"));
          node.addChildren(XValueChildrenList.singleton(name, result), true);
          myDialog.evaluationDone();
        }

        @Override
        public void errorOccurred(@NotNull final String errorMessage) {
          node.setErrorMessage(errorMessage);
          myDialog.evaluationDone();
        }
      });
    }
  }
}
