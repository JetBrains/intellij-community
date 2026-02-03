// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree

import org.jetbrains.annotations.ApiStatus
import javax.swing.event.TreeModelEvent
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

@ApiStatus.Internal
interface TreeModelUpdateRequest {
  fun nodesLoaded(count: Int)
  fun finished()
}

@ApiStatus.Internal
class RequestedTreeModelUpdateEvent(
  val request: TreeModelUpdateRequest?,
  model: TreeModel,
  path: TreePath?,
  indices: IntArray?,
  children: Array<Any>?,
) : TreeModelEvent(model, path, indices, children) {
  override fun toString(): String {
    return "RequestedTreeModelUpdateEvent(request=$request) ${super.toString()}"
  }
}
