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
    super(tree, null, new EvaluatingResultContainer(evaluationDialog));
    setLeaf(false);
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
