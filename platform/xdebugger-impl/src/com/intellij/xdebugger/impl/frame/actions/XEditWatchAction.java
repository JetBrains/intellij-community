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
package com.intellij.xdebugger.impl.frame.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchesRootNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public class XEditWatchAction extends XWatchesTreeActionBase {
  @Override
  public void update(@NotNull final AnActionEvent e) {
    XDebuggerTree tree = XDebuggerTree.getTree(e);
    e.getPresentation().setEnabledAndVisible(tree != null && getSelectedNodes(tree, WatchNodeImpl.class).size() == 1);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
  @Override
  protected void perform(@NotNull AnActionEvent e, @NotNull XDebuggerTree tree, @NotNull XWatchesView watchesView) {
    List<? extends WatchNodeImpl> watchNodes = getSelectedNodes(tree, WatchNodeImpl.class);
    if (watchNodes.size() != 1) return;

    WatchNodeImpl node = watchNodes.get(0);
    XDebuggerTreeNode root = tree.getRoot();
    if (root instanceof WatchesRootNode) {
      ((WatchesRootNode)root).editWatch(node);
    }
  }
}
