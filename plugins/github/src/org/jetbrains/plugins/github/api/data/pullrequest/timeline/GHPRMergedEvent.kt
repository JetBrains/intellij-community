// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest.timeline

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.GHCommitShort
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestState
import java.util.*

data class GHPRMergedEvent(override val actor: GHActor?,
                           override val createdAt: Date,
                           val commit: GHCommitShort?,
                           val mergeRefName: @NlsSafe String)
  : GHPRTimelineEvent.State {
  override val newState = GHPullRequestState.MERGED
}