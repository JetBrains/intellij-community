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

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.impl.frame.actions.XWatchesTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNode;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.util.List;

@ApiStatus.Internal
public abstract class XCopyValueAction extends XFetchValueSplitActionBase {
  @Override
  protected @NotNull ValueCollector createCollector(@NotNull AnActionEvent e) {
    XDebuggerTree tree = XDebuggerTree.getTree(e);
    return new ValueCollector(e.getProject()) {
      @Override
      public void handleInCollector(Project project, String value) {
        if (tree == null) return;
        List<? extends WatchNode> watchNodes = XWatchesTreeActionBase.getSelectedNodes(tree, WatchNode.class);
        if (watchNodes.isEmpty()) {
          handle(project, value);
        }
        else {
          CopyPasteManager.getInstance().setContents(
            new XWatchTransferable(value, ContainerUtil.map(watchNodes, WatchNode::getExpression)));
        }
      }
    };
  }

  @Override
  protected void handle(Project project, String value) {
    CopyPasteManager.getInstance().setContents(new StringSelection(value));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  static class Simple extends XCopyValueAction {
  }
}
