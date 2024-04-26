// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

public class CheckboxTree extends CheckboxTreeBase {

  public abstract static class CheckboxTreeCellRenderer extends CheckboxTreeCellRendererBase { // This is 6.0 compatibility layer
    protected CheckboxTreeCellRenderer() {
    }

    protected CheckboxTreeCellRenderer(final boolean opaque) {
      super(opaque);
    }

    protected CheckboxTreeCellRenderer(boolean opaque, boolean usePartialStatusForParentNodes) {
      super(opaque, usePartialStatusForParentNodes);
    }
  }

  public CheckboxTree(final CheckboxTreeCellRenderer cellRenderer, CheckedTreeNode root) {
    super(cellRenderer, root);

    installSpeedSearch();
  }

  // for designer
  public CheckboxTree() {
  }

  public CheckboxTree(final CheckboxTreeCellRenderer cellRenderer, CheckedTreeNode root, final CheckPolicy checkPolicy) {
    super(cellRenderer, root, checkPolicy);

    installSpeedSearch();
  }

  protected void installSpeedSearch() {
    TreeUIHelper.getInstance().installTreeSpeedSearch(this);
  }
}
