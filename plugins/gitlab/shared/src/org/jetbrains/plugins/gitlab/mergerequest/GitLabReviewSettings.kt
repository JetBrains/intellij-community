// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

/**
 * The settings of the merge request review: the review in editors, the new notes in every view, and the timeline.
 *
 * `GitLabMergeRequestsPreferences` holds more settings and is internal to the backend module, so the review models
 * depend on this narrower view instead.
 *
 * The backend registers this service.
 */
@ApiStatus.Internal
interface GitLabReviewSettings {
  var highlightDiffLinesInEditor: Boolean
  val highlightDiffLinesInEditorState: StateFlow<Boolean>

  /**
   * Whether the last submitted note was a draft note. The submit button uses the same kind of submission as the last time.
   */
  var usedAsDraftSubmitActionLast: Boolean
  val usedAsDraftSubmitActionLastState: StateFlow<Boolean>

  /**
   * Whether the review in editors shows the discussions.
   */
  var editorReviewEnabled: Boolean

  /**
   * Whether the timeline shows the events of the merge request, such as the changes of its state and labels.
   */
  var showEventsInTimeline: Boolean

  companion object {
    fun getInstance(project: Project): GitLabReviewSettings = project.service()
  }
}
