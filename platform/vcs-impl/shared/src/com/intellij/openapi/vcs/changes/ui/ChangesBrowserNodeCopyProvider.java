// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.datatransfer.StringSelection;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

class ChangesBrowserNodeCopyProvider implements CopyProvider {

  private final @NotNull JTree myTree;

  ChangesBrowserNodeCopyProvider(@NotNull JTree tree) {
    myTree = tree;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public boolean isCopyEnabled(@NotNull DataContext dataContext) {
    return myTree.getSelectionPaths() != null;
  }

  @Override
  public boolean isCopyVisible(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public void performCopy(@NotNull DataContext dataContext) {
    List<TreePath> paths = ContainerUtil.sorted(Arrays.asList(Objects.requireNonNull(myTree.getSelectionPaths())),
                                                TreeUtil.getDisplayOrderComparator(myTree));
    CopyPasteManager.getInstance().setContents(new StringSelection(StringUtil.join(paths, path -> {
      Object node = path.getLastPathComponent();
      if (node instanceof ChangesBrowserNode) {
        return ((ChangesBrowserNode<?>)node).getTextPresentation();
      }
      else {
        return node.toString();
      }
    }, "\n")));
  }
}
