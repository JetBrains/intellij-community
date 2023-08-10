// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data.pullrequest

enum class GHPullRequestFileViewedState {
  DISMISSED, UNVIEWED, VIEWED
}

class GHPullRequestChangedFile(
  val path: String,
  val viewerViewedState: GHPullRequestFileViewedState
)

internal fun GHPullRequestFileViewedState.isViewed(): Boolean = this == GHPullRequestFileViewedState.VIEWED