/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.frame.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.util.ObjectUtils;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author egor
 */
public class XCopyWatchAction extends XWatchesTreeActionBase {

  protected boolean isEnabled(@NotNull AnActionEvent e, @NotNull XDebuggerTree tree) {
    return !getSelectedNodes(tree, XValueNodeImpl.class).isEmpty();
  }

  @Override
  protected void perform(@NotNull AnActionEvent e, @NotNull XDebuggerTree tree, @NotNull XWatchesView watchesView) {
    XDebuggerTreeNode root = tree.getRoot();
    List<? extends XValueNodeImpl> nodes = getSelectedNodes(tree, XValueNodeImpl.class);
    for (XValueNodeImpl node : nodes) {
      int index = root.getIndex(node);
      boolean isWatch = node instanceof WatchNode;
      XExpression expression = isWatch ? ((WatchNode)node).getExpression() :
                                XExpressionImpl.fromText(node.getName());
      if (expression == null) continue;
      node.getValueContainer().calculateEvaluationExpression().done(
        expr -> DebuggerUIUtil.invokeLater(
          () -> watchesView.addWatchExpression(ObjectUtils.notNull(expr, expression), isWatch ? index + 1 : -1, true)));

    }
  }
}
