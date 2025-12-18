// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ui.tree.TreeNodePresentationBuilderImpl
import com.intellij.ui.treeStructure.TreeNodePresentation
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
@Serializable
sealed interface ProjectViewPaneProviderId {
  val idString: @NonNls String
}

@ApiStatus.Internal
@Serializable
sealed interface ProjectViewPaneId {
  val idString: @NonNls String
}

@ApiStatus.Internal
fun projectViewPaneProviderId(idString: @NonNls String): ProjectViewPaneProviderId = ProjectViewPaneProviderIdImpl(idString)

@ApiStatus.Internal
fun projectViewPaneId(idString: @NonNls String): ProjectViewPaneId = ProjectViewPaneIdImpl(idString)

private data class ProjectViewPaneProviderIdImpl(
  override val idString: @NonNls String
) : ProjectViewPaneProviderId

private data class ProjectViewPaneIdImpl(
  override val idString: @NonNls String
) : ProjectViewPaneId

@ApiStatus.Internal
const val SUPER_ROOT_ID: Long = 0L

@ApiStatus.Internal
object SuperRoot

@ApiStatus.Internal
val SuperRootPresentation: TreeNodePresentation = TreeNodePresentationBuilderImpl(false).also {
  it.setMainText("fake root - for convenience, not to display")
}.build()
