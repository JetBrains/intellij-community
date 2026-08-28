// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.openapi.util.NlsSafe
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@Serializable
internal data class ProjectViewPaneIdImpl(
  override val idString: @NonNls String
) : ProjectViewPaneId

@ApiStatus.Internal
@Serializable
data class ProjectViewPaneDescriptorImpl(
  val id: ProjectViewPaneId,
  val kind: ProjectViewPaneKind,
  val presentableName: @NlsSafe String,
  val order: Int,
  val isDefault: Boolean,
  val selectInTargetDescriptors: List<SelectInTargetDescriptor>,
) : ProjectViewPaneDescriptor

@ApiStatus.Internal
@Serializable
enum class ProjectViewPaneKind {
  BACKEND,
  LIGHT,
  UI_ONLY,
}

@ApiStatus.Internal
class ProjectViewPaneDescriptorBuilderImpl : ProjectViewPaneDescriptorBuilder {
  var kind: ProjectViewPaneKind = ProjectViewPaneKind.BACKEND
  private var isDefault = false
  private val selectInTargets = mutableListOf<SelectInTargetDescriptor>()

  override fun setDefault(isDefault: Boolean) {
    this.isDefault = isDefault
  }

  override fun addSelectInTarget(
    id: @NonNls String,
    presentableName: @NlsSafe String,
    weight: Float,
  ) {
    selectInTargets += SelectInTargetDescriptor(id, presentableName, weight)
  }

  override fun build(
    id: ProjectViewPaneId,
    presentableName: @NlsSafe String,
    order: Int,
  ): ProjectViewPaneDescriptor {
    return ProjectViewPaneDescriptorImpl(
      id = id,
      kind = kind,
      presentableName = presentableName,
      order = order,
      isDefault = isDefault,
      selectInTargetDescriptors = selectInTargets.toImmutableList(),
    )
  }
}
