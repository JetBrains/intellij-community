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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.CommonActionsPanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.frame.XWatchesViewImpl;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchesRootNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XDebuggerTreeNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author egor
 */
public class XMoveWatchDown extends XWatchesTreeActionBase {
  public XMoveWatchDown() {
    getTemplatePresentation().setIcon(CommonActionsPanel.Buttons.DOWN.getIcon());
  }

  protected boolean isEnabled(@NotNull AnActionEvent e, @NotNull XDebuggerTree tree) {
    List<? extends WatchNodeImpl> nodes = getSelectedNodes(tree, WatchNodeImpl.class);
    if (nodes.size() == 1) {
      XDebuggerTreeNode root = tree.getRoot();
      if (root instanceof WatchesRootNode) {
        return root.getIndex(nodes.get(0)) < ((WatchesRootNode)root).getWatchChildren().size() - 1;
      }
    }
    return false;
  }

  @Override
  protected void perform(@NotNull AnActionEvent e, @NotNull XDebuggerTree tree, @NotNull XWatchesView watchesView) {
    if (watchesView instanceof XWatchesViewImpl) {
      ((XWatchesViewImpl)watchesView).moveWatchDown(ContainerUtil.getFirstItem(getSelectedNodes(tree, WatchNodeImpl.class)));
    }
  }
}
