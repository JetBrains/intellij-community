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

import com.intellij.openapi.util.registry.Registry;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.frame.XDebugView;
import com.intellij.xdebugger.impl.ui.XDebugSessionData;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XStackFrameNode extends XValueContainerNode<XStackFrame> {
  public XStackFrameNode(final @NotNull XDebuggerTree tree, final @NotNull XStackFrame xStackFrame) {
    super(tree, null, xStackFrame);
    setLeaf(false);
  }

  @Override
  public void startComputingChildren() {
    if (Registry.is("debugger.watches.in.variables")) {
      XDebugSession session = XDebugView.getSession(getTree());
      XDebuggerEvaluator evaluator = getValueContainer().getEvaluator();
      if (session != null && evaluator != null) {
        XDebugSessionData data = ((XDebugSessionImpl)session).getSessionData();
        XExpression[] expressions = data.getWatchExpressions();
        for (final XExpression expression : expressions) {
          evaluator.evaluate(expression, new XDebuggerEvaluator.XEvaluationCallback() {
            @Override
            public void evaluated(@NotNull XValue result) {
              addChildren(XValueChildrenList.singleton(expression.getExpression(), result), false);
            }

            @Override
            public void errorOccurred(@NotNull String errorMessage) {
              // do not add anything
            }
          }, getValueContainer().getSourcePosition());
        }
      }
    }
    super.startComputingChildren();
  }
}
