// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details.model

import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.plugins.github.api.data.GHCommit

internal interface GHPRCommitsViewModel {
  val reviewCommits: StateFlow<List<GHCommit>>
  val selectedCommit: StateFlow<GHCommit?>
  val selectedCommitIndex: StateFlow<Int>

  fun selectCommit(commit: GHCommit?)

  fun selectAllCommits()

  fun selectNextCommit()

  fun selectPreviousCommit()
}