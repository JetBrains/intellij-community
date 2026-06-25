// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DestructuringDeclaration")

package com.intellij.platform.projectView.pane

import com.intellij.platform.projectView.actions.ProjectViewActionState
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed interface ProjectViewPaneStateBuilder {
  suspend fun setNodeChildren(parentId: Long, children: List<ProjectViewNodeModel>)
  suspend fun addNode(parentId: Long, index: Int, nodeModel: ProjectViewNodeModel)
  suspend fun updateNode(nodeModel: ProjectViewNodeModel)
  suspend fun removeNodeChildren(parentId: Long)
  suspend fun removeNodeChild(parentId: Long, index: Int)
  suspend fun updateActionState(actionState: ProjectViewActionState)
  suspend fun clear()
}
