@file:ApiStatus.Experimental
// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

@ApiStatus.Experimental
@Serializable
sealed interface ProjectViewPaneId : Comparable<ProjectViewPaneId> {
  companion object {
    val DATA_KEY: DataKey<ProjectViewPaneId> = DataKey.create("ProjectViewPaneId")
  }

  val idString: @NonNls String

  override fun compareTo(other: ProjectViewPaneId): Int = idString.compareTo(other.idString)
}

@ApiStatus.Experimental
fun projectViewPaneId(idString: @NonNls String): ProjectViewPaneId = ProjectViewPaneIdImpl(idString)

@ApiStatus.Experimental
sealed interface ProjectViewPaneDescriptor

@ApiStatus.Experimental
sealed interface ProjectViewPaneDescriptorBuilder {
  fun setDefault(isDefault: Boolean)

  fun setIcon(icon: Icon)

  fun addSelectInTarget(
    id: @NonNls String,
    presentableName: @NlsSafe String,
    weight: Float,
  )

  fun build(id: ProjectViewPaneId, presentableName: @NlsSafe String, order: Int): ProjectViewPaneDescriptor
}
