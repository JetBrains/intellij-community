// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.util.NlsSafe
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
@Serializable
sealed interface ProjectViewPaneId : Comparable<ProjectViewPaneId> {
  companion object {
    val DATA_KEY: DataKey<ProjectViewPaneId> = DataKey.create("ProjectViewPaneId")
  }

  val idString: @NonNls String

  override fun compareTo(other: ProjectViewPaneId): Int = idString.compareTo(other.idString)
}

@ApiStatus.Internal
fun projectViewPaneId(idString: @NonNls String): ProjectViewPaneId = ProjectViewPaneIdImpl(idString)

@Serializable
private data class ProjectViewPaneIdImpl(
  override val idString: @NonNls String
) : ProjectViewPaneId

@ApiStatus.Internal
@Serializable
sealed interface ProjectViewPaneDescriptor

@ApiStatus.Internal
@Serializable
sealed interface ProjectViewPaneDescriptorBuilder {
  fun setDefault(isDefault: Boolean)
  fun addSelectInTarget(selectInTargetDescriptor: SelectInTargetDescriptor)
  fun build(id: ProjectViewPaneId, presentableName: @NlsSafe String, order: Int): ProjectViewPaneDescriptor
}

@ApiStatus.Internal
class ProjectViewPaneDescriptorBuilderImpl : ProjectViewPaneDescriptorBuilder {
  private var isDefault = false
  private val selectInTargets = mutableListOf<SelectInTargetDescriptor>()

  override fun setDefault(isDefault: Boolean) {
    this.isDefault = isDefault
  }

  override fun addSelectInTarget(selectInTargetDescriptor: SelectInTargetDescriptor) {
    selectInTargets += selectInTargetDescriptor
  }

  override fun build(
    id: ProjectViewPaneId,
    presentableName: @NlsSafe String,
    order: Int,
  ): ProjectViewPaneDescriptor {
    return ProjectViewPaneDescriptorImpl(
      id = id,
      presentableName = presentableName,
      order = order,
      isDefault = isDefault,
      selectInTargetDescriptors = selectInTargets.toImmutableList(),
    )
  }
}

@ApiStatus.Internal
@Serializable
data class ProjectViewPaneDescriptorImpl(
  val id: ProjectViewPaneId,
  val presentableName: @NlsSafe String,
  val order: Int,
  val isDefault: Boolean,
  val selectInTargetDescriptors: List<SelectInTargetDescriptor>,
) : ProjectViewPaneDescriptor
