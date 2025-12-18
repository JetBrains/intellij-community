// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ui.treeStructure.TreeNodePresentation
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
sealed class ProjectViewPaneStateEvent

@ApiStatus.Internal
@Serializable
data class ProjectViewNodeAdded(
  val parentId: Long,
  val index: Int,
  val nodeId: Long,
  val presentation: TreeNodePresentation,
) : ProjectViewPaneStateEvent()

@ApiStatus.Internal
@Serializable
data class ProjectViewChildRemoved(
  val parentId: Long,
  val index: Int,
) : ProjectViewPaneStateEvent()

@ApiStatus.Internal
@Serializable
data class ProjectViewChildrenRemoved(val parentId: Long) : ProjectViewPaneStateEvent()

@ApiStatus.Internal
@Serializable
data class ProjectViewNodeUpdated(
  val nodeId: Long,
  val presentation: TreeNodePresentation,
) : ProjectViewPaneStateEvent()

@ApiStatus.Internal
@Serializable
sealed class ProjectViewPaneRequest

@ApiStatus.Internal
@Serializable
data class ProjectViewPaneLoadChildrenRequest(
  val nodeId: Long,
) : ProjectViewPaneRequest()