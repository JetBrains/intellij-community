// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.psiViewer.formatter;

import com.intellij.ui.treeStructure.SimpleTreeStructure;
import org.jetbrains.annotations.NotNull;

public class BlockTreeStructure extends SimpleTreeStructure {
  private BlockTreeNode myRoot;

  @Override
  public @NotNull BlockTreeNode getRootElement() {
    return myRoot;
  }

  public void setRoot(BlockTreeNode root) {
    myRoot = root;
  }
}
