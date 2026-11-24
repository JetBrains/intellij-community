// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView

import com.intellij.platform.projectView.pane.ProjectViewPaneId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * The panes of the Project View tool window and its current pane, taken at one moment.
 *
 * Get the instance from [ProjectView.panes]. The instance is immutable.
 */
@ApiStatus.Experimental
sealed interface ProjectViewPanes {
  /**
   * The IDs of the panes that the tool window shows, in the shown order.
   */
  val paneIds: List<ProjectViewPaneId>

  /**
   * The ID of the current pane, or `null` if the tool window has no current pane.
   * A non-null value is always one of [paneIds].
   */
  val currentPaneId: ProjectViewPaneId?

  /**
   * Returns `true` if [other] is a [ProjectViewPanes] with equal [paneIds] and an equal [currentPaneId].
   */
  override fun equals(other: Any?): Boolean

  override fun hashCode(): Int
}

@ApiStatus.Internal
@Serializable
data class ProjectViewPanesImpl(
  override val paneIds: List<ProjectViewPaneId>,
  override val currentPaneId: ProjectViewPaneId?,
) : ProjectViewPanes {
  init {
    require(currentPaneId == null || currentPaneId in paneIds) {
      "The current pane $currentPaneId is not one of the panes $paneIds"
    }
  }

  companion object {
    val EMPTY: ProjectViewPanesImpl = ProjectViewPanesImpl(emptyList(), null)

    fun of(panes: ProjectViewPanes): ProjectViewPanesImpl = when (panes) {
      is ProjectViewPanesImpl -> panes
    }
  }
}
