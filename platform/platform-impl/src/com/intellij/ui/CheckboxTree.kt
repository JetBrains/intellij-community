// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

open class CheckboxTree : CheckboxTreeBase {
  abstract class CheckboxTreeCellRenderer : CheckboxTreeCellRendererBase {
    // This is 6.0 compatibility layer
    protected constructor()

    protected constructor(opaque: Boolean) : super(opaque)

    protected constructor(opaque: Boolean, usePartialStatusForParentNodes: Boolean) : super(opaque, usePartialStatusForParentNodes)
  }

  @Deprecated("provide `checkPolicy` explicitly, as the default one is defective")
  constructor(cellRenderer: CheckboxTreeCellRenderer, root: CheckedTreeNode?) : super(cellRenderer, root) {
    installSpeedSearch()
  }

  // for designer
  constructor()

  constructor(cellRenderer: CheckboxTreeCellRenderer, root: CheckedTreeNode?, checkPolicy: CheckPolicy) : super(cellRenderer, root,
                                                                                                                checkPolicy) {
    installSpeedSearch()
  }

  protected open fun installSpeedSearch() {
    TreeUIHelper.getInstance().installTreeSpeedSearch(this)
  }
}
