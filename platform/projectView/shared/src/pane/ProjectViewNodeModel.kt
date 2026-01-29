// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ui.tree.TreeNodePresentationBuilderImpl
import com.intellij.ui.treeStructure.TreeNodePresentationImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class ProjectViewNodeModel(
  val id: Long,
  val presentation: TreeNodePresentationImpl,
  val flags: Int = 0,
) {
  constructor(
    id: Long,
    presentation: TreeNodePresentationImpl,
    canNavigate: Boolean,
    canNavigateToSource: Boolean,
  ) : this(
    id,
    presentation,
    flags(canNavigate, canNavigateToSource),
  )

  fun canNavigate(): Boolean = (flags and FLAG_CAN_NAVIGATE) != 0

  fun canNavigateToSource(): Boolean = (flags and FLAG_CAN_NAVIGATE_TO_SOURCE) != 0
}

private const val FLAG_CAN_NAVIGATE = (1 shl 0)
private const val FLAG_CAN_NAVIGATE_TO_SOURCE = (1 shl 1)

private fun flags(canNavigate: Boolean, canNavigateToSource: Boolean): Int =
  (if (canNavigate) FLAG_CAN_NAVIGATE else 0) or
  (if (canNavigateToSource) FLAG_CAN_NAVIGATE_TO_SOURCE else 0)

@ApiStatus.Internal
const val SUPER_ROOT_ID: Long = 0L

@ApiStatus.Internal
object SuperRoot

@ApiStatus.Internal
val SuperRootPresentation: TreeNodePresentationImpl = TreeNodePresentationBuilderImpl(false).also {
  it.setMainText("fake root - for convenience, not to display")
}.build()

@ApiStatus.Internal
val SuperRootModel: ProjectViewNodeModel = ProjectViewNodeModel(SUPER_ROOT_ID, SuperRootPresentation)
