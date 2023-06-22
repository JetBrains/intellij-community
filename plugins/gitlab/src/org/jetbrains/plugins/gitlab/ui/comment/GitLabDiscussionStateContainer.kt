// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class GitLabDiscussionStateContainer(
  val resolved: Flow<Boolean>,
  val outdated: Flow<Boolean>
) {
  companion object {
    val DEFAULT: GitLabDiscussionStateContainer = GitLabDiscussionStateContainer(flowOf(false), flowOf(false))
  }
}