// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineEvent

class GHPRTimelineMergedStateEvents(initialState: GHPRTimelineEvent.State) : GHPRTimelineMergedEvents<GHPRTimelineEvent.State>(), GHPRTimelineEvent.State {
  private val inferredOriginalState: GHPullRequestState = when (initialState.newState) {
    GHPullRequestState.CLOSED -> GHPullRequestState.OPEN
    GHPullRequestState.MERGED -> GHPullRequestState.OPEN
    GHPullRequestState.OPEN -> GHPullRequestState.CLOSED
  }

  init {
    add(initialState)
  }

  override var newState: GHPullRequestState = initialState.newState
    private set

  var lastStateEvent = initialState
    private set

  override fun addNonMergedEvent(event: GHPRTimelineEvent.State) {
    if (newState != GHPullRequestState.MERGED) {
      newState = event.newState
      lastStateEvent = event
    }
  }

  override fun hasAnyChanges(): Boolean = newState != inferredOriginalState
}