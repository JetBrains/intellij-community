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

/**
 * A group of Select In targets.
 *
 * The Select In popup shows the group as one item, and the targets of the group as its children.
 * The targets of several panes can refer to one group.
 */
@ApiStatus.Experimental
sealed interface SelectInTargetGroup

/**
 * Creates a group of Select In targets.
 *
 * The [id] identifies the group. If several panes create a group with the same [id], the first one decides the
 * [presentableName] and the [weight].
 */
@ApiStatus.Experimental
fun selectInTargetGroup(id: @NonNls String, presentableName: @NlsSafe String, weight: Float): SelectInTargetGroup =
  SelectInTargetGroupImpl(id, presentableName, weight)

@ApiStatus.Experimental
sealed interface ProjectViewPaneDescriptorBuilder {
  fun setDefault(isDefault: Boolean)

  fun setIcon(icon: Icon)

  /**
   * Adds a Select In target that selects in this pane.
   *
   * A pane can add any number of targets. A target with a [group] shows as a child of that group.
   */
  fun addSelectInTarget(
    presentableName: @NlsSafe String,
    weight: Float,
    group: SelectInTargetGroup? = null,
  )

  fun build(id: ProjectViewPaneId, presentableName: @NlsSafe String, order: Int): ProjectViewPaneDescriptor
}
