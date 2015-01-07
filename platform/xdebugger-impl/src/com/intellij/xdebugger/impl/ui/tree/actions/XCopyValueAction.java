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
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.impl.frame.actions.XWatchesTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchNode;

import java.awt.datatransfer.StringSelection;
import java.util.List;

/**
 * @author nik
 */
public class XCopyValueAction extends XFetchValueActionBase {
  @Override
  protected void handle(final Project project, final String value, XDebuggerTree tree) {
    if (tree == null) return;
    List<? extends WatchNode> watchNodes = XWatchesTreeActionBase.getSelectedNodes(tree, WatchNode.class);
    if (watchNodes.isEmpty()) {
      CopyPasteManager.getInstance().setContents(new StringSelection(value));
    }
    else {
      CopyPasteManager.getInstance().setContents(
        new XWatchTransferable(value, ContainerUtil.map(watchNodes,
                                                        new Function<WatchNode, XExpression>() {
                                                          @Override
                                                          public XExpression fun(WatchNode node) {
                                                            return node.getExpression();
                                                          }
                                                        })));
    }
  }
}
