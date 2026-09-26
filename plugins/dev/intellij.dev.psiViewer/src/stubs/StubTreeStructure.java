// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.psiViewer.stubs;

import com.intellij.ui.treeStructure.SimpleTreeStructure;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin.Ulitin
 */
public class StubTreeStructure extends SimpleTreeStructure {

  private final StubTreeNode myRoot;

  public StubTreeStructure(StubTreeNode root) {
    myRoot = root;
  }

  @Override
  public @NotNull StubTreeNode getRootElement() {
    return myRoot;
  }
}
