// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DestructuringDeclaration")

package com.intellij.platform.projectView.pane

import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsAccessor
import com.intellij.platform.projectView.settings.ProjectViewPaneSettingsStateBuilder
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
sealed interface ProjectViewPaneStateBuilder {
  suspend fun setNodeChildren(parentId: Long, children: List<ProjectViewNodeModel>)
  suspend fun addNode(parentId: Long, index: Int, nodeModel: ProjectViewNodeModel)
  suspend fun updateNode(nodeModel: ProjectViewNodeModel)
  suspend fun removeNodeChildren(parentId: Long)
  suspend fun removeNodeChild(parentId: Long, index: Int)
  suspend fun updateSettingsState(build: (ProjectViewPaneSettingsStateBuilder) -> Unit)
  suspend fun clear()
  fun <T> asBackendStateAccessor(): BackendProjectViewPaneStateAccessor<T>
  fun asSettingsAccessor(): ProjectViewPaneSettingsAccessor
}

@ApiStatus.Experimental
sealed interface BackendProjectViewPaneStateAccessor<T> {
  fun getNodeById(id: Long): BackendProjectViewNodeModel<T>?
  fun getNodeByUserObject(userObject: T): BackendProjectViewNodeModel<T>?
  fun getChildren(parent: BackendProjectViewNodeModel<T>?): List<BackendProjectViewNodeModel<T>>?
}
