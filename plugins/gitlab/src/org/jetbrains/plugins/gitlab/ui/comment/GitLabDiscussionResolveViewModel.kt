// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui.comment

import com.intellij.util.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabDiscussion
import org.jetbrains.plugins.gitlab.util.SingleCoroutineLauncher

interface GitLabDiscussionResolveViewModel {
  val resolved: Flow<Boolean>
  val busy: Flow<Boolean>

  fun changeResolvedState()
}

class GitLabDiscussionResolveViewModelImpl(parentCs: CoroutineScope, private val discussion: GitLabDiscussion)
  : GitLabDiscussionResolveViewModel {

  override val resolved: Flow<Boolean> = discussion.resolved

  private val taskLauncher = SingleCoroutineLauncher(parentCs.childScope())
  override val busy: Flow<Boolean> = taskLauncher.busy

  override fun changeResolvedState() {
    taskLauncher.launch {
      try {
        discussion.changeResolvedState()
      }
      catch (e: Exception) {
        if (e is CancellationException) throw e
        //TODO: handle???
      }
    }
  }
}
