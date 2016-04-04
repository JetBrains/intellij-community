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
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.icons.AllIcons;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public class WatchMessageNode extends MessageTreeNode implements WatchNode {
  private final XExpression myExpression;
  private volatile boolean myObsolete;

  private WatchMessageNode(XDebuggerTree tree, XDebuggerTreeNode parent, @NotNull XExpression expression, final Icon icon) {
    super(tree, parent, true);
    myExpression = expression;
    setIcon(icon);
  }

  @Override
  @NotNull
  public XExpression getExpression() {
    return myExpression;
  }

  public static WatchMessageNode createMessageNode(XDebuggerTree tree, XDebuggerTreeNode parent, XExpression expression) {
    final WatchMessageNode node = new WatchMessageNode(tree, parent, expression, AllIcons.Debugger.Watch);
    node.myText.append(expression.getExpression(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    return node;
  }

  public static WatchMessageNode createEvaluatingNode(XDebuggerTree tree, XDebuggerTreeNode parent, XExpression expression) {
    final WatchMessageNode node = new WatchMessageNode(tree, parent, expression, AllIcons.Debugger.Watch);
    node.myText.append(expression + " = ...", XDebuggerUIConstants.EVALUATING_EXPRESSION_HIGHLIGHT_ATTRIBUTES);
    return node;
  }

  public static WatchMessageNode createErrorNode(XDebuggerTree tree, XDebuggerTreeNode parent, @NotNull XExpression expression, @NotNull String errorMessage) {
    final WatchMessageNode node = new WatchMessageNode(tree, parent, expression, XDebuggerUIConstants.ERROR_MESSAGE_ICON);
    node.myText.append(expression + " = ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
    node.myText.append(errorMessage, SimpleTextAttributes.ERROR_ATTRIBUTES);
    return node;
  }

  @Override
  public String toString() {
    return myExpression.getExpression();
  }

  @Override
  public void setObsolete() {
    myObsolete = true;
  }

  @Override
  public boolean isObsolete() {
    return myObsolete;
  }
}
