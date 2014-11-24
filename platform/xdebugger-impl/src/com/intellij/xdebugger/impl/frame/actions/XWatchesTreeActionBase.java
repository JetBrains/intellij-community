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
package com.intellij.xdebugger.impl.frame.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.impl.frame.XWatchesView;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public abstract class XWatchesTreeActionBase extends AnAction {
  @NotNull
  public static <T extends TreeNode> List<? extends T> getSelectedNodes(final @NotNull XDebuggerTree tree, Class<T> nodeClass) {
    List<T> list = new ArrayList<T>();
    TreePath[] selectionPaths = tree.getSelectionPaths();
    if (selectionPaths != null) {
      for (TreePath selectionPath : selectionPaths) {
        Object element = selectionPath.getLastPathComponent();
        if (nodeClass.isInstance(element)) {
          list.add(nodeClass.cast(element));
        }
      }
    }
    return list;
  }

  public void update(final AnActionEvent e) {
    final XDebuggerTree tree = XDebuggerTree.getTree(e);
    XWatchesView watchesView = e.getData(XWatchesView.DATA_KEY);
    boolean enabled = tree != null && watchesView != null && isEnabled(e, tree);
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final XDebuggerTree tree = XDebuggerTree.getTree(e);
    XWatchesView watchesView = e.getData(XWatchesView.DATA_KEY);
    if (tree != null && watchesView != null) {
      perform(e, tree, watchesView);
    }
  }

  protected abstract void perform(@NotNull AnActionEvent e, @NotNull XDebuggerTree tree, @NotNull XWatchesView watchesView);

  protected boolean isEnabled(@NotNull AnActionEvent e, @NotNull XDebuggerTree tree) {
    return true;
  }
}
