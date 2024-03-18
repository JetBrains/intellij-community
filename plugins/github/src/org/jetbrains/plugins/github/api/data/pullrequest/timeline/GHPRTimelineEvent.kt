// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline

import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import java.util.*

interface GHPRTimelineEvent : GHPRTimelineItem {
  val actor: GHActor?
  override val createdAt: Date

  /**
   * Simple events which can be merged together
   */
  interface Simple : GHPRTimelineEvent

  /**
   * Events about pull request state
   */
  interface State : GHPRTimelineEvent {
    val newState: GHPullRequestState
  }

  /**
   * More complex events which can NOT be merged together
   */
  interface Complex : GHPRTimelineEvent

  /**
   * Pull request head/base branch changes events
   */
  interface Branch : Complex
}