// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.vcs.log.graph.NodePrintElement

internal class HeadNodePrintElement(
  element: NodePrintElement,
) : NodePrintElement by element {
  override fun getNodeType(): NodePrintElement.Type {
    return NodePrintElement.Type.OUTLINE_AND_FILL
  }
}
