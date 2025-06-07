// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework;

import com.intellij.ide.util.treeView.AbstractTreeStructure;
import org.jetbrains.annotations.NotNull;

public abstract class TestTreeViewStructure<T extends AbstractTestProxy> extends AbstractTreeStructure {
  private Filter<T> myTestNodesFilter = Filter.NO_FILTER;

  public Filter<T> getFilter() {
    return myTestNodesFilter;
  }

  public void setFilter(final @NotNull Filter<T> nodesFilter) {
    myTestNodesFilter = nodesFilter;
  }
}
