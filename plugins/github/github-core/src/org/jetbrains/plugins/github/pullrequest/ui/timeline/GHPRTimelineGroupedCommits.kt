// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestCommitShort
import java.util.*

class GHPRTimelineGroupedCommits : GHPRTimelineGroupedItems<GHPullRequestCommitShort>() {
  override val createdAt: Date?
    get() = items.mapNotNull { it.createdAt }.maxOrNull()
}