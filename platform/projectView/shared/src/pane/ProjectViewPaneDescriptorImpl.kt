// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.util.NlsSafe
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

@Serializable
internal data class ProjectViewPaneIdImpl(
  override val idString: @NonNls String
) : ProjectViewPaneId

@ApiStatus.Internal
data class ProjectViewPaneDescriptorImpl(
  val id: ProjectViewPaneId,
  val kind: ProjectViewPaneKind,
  val presentableName: @NlsSafe String,
  val order: Int,
  val isDefault: Boolean,
  val icon: Icon?,
  val selectInTargetDescriptors: List<SelectInTargetDescriptor>,
) : ProjectViewPaneDescriptor {
  fun toDTO(): ProjectViewPaneDescriptorDTO {
    if (kind != ProjectViewPaneKind.BACKEND) {
      throw IllegalArgumentException("Only BACKEND descriptors should be serialized, but got $this")
    }
    return ProjectViewPaneDescriptorDTO(
      id = id,
      presentableName = presentableName,
      order = order,
      isDefault = isDefault,
      iconId = icon?.rpcId(),
      selectInTargetDescriptors = selectInTargetDescriptors
    )
  }
}

@ApiStatus.Internal
@Serializable
data class ProjectViewPaneDescriptorDTO(
  val id: ProjectViewPaneId,
  val presentableName: @NlsSafe String,
  val order: Int,
  val isDefault: Boolean,
  val iconId: IconId?,
  val selectInTargetDescriptors: List<SelectInTargetDescriptor>,
) {
  fun toDescriptor(): ProjectViewPaneDescriptorImpl = ProjectViewPaneDescriptorImpl(
    id = id,
    kind = ProjectViewPaneKind.BACKEND, // only backend descriptors are ever serialized
    presentableName = presentableName,
    order = order,
    isDefault = isDefault,
    icon = iconId?.icon(),
    selectInTargetDescriptors = selectInTargetDescriptors
  )
}

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
  private var icon: Icon? = null
  private val selectInTargets = mutableListOf<SelectInTargetDescriptor>()

  override fun setDefault(isDefault: Boolean) {
    this.isDefault = isDefault
  }

  override fun setIcon(icon: Icon) {
    this.icon = icon
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
      icon = icon,
      selectInTargetDescriptors = selectInTargets.toImmutableList(),
    )
  }
}
