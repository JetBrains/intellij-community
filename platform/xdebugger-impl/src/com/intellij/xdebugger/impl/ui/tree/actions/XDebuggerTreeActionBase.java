/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class XDebuggerTreeActionBase extends AnAction {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    XValueNodeImpl node = getSelectedNode(e.getDataContext());
    if (node != null) {
      String nodeName = node.getName();
      if (nodeName != null) {
        perform(node, nodeName, e);
      }
    }
  }

  protected abstract void perform(final XValueNodeImpl node, @NotNull String nodeName, final AnActionEvent e);

  @Override
  public void update(@NotNull final AnActionEvent e) {
    XValueNodeImpl node = getSelectedNode(e.getDataContext());
    e.getPresentation().setEnabled(node != null && isEnabled(node, e));
  }

  protected boolean isEnabled(final @NotNull XValueNodeImpl node, @NotNull AnActionEvent e) {
    return node.getName() != null;
  }

  public static @NotNull List<XValueNodeImpl> getSelectedNodes(@NotNull DataContext dataContext) {
    return XDebuggerTree.getSelectedNodes(dataContext);
  }

  public static @Nullable XValueNodeImpl getSelectedNode(@NotNull DataContext dataContext) {
    return ContainerUtil.getFirstItem(getSelectedNodes(dataContext));
  }

  @Nullable
  public static XValue getSelectedValue(@NotNull DataContext dataContext) {
    XValueNodeImpl node = getSelectedNode(dataContext);
    return node != null ? node.getValueContainer() : null;
  }
}