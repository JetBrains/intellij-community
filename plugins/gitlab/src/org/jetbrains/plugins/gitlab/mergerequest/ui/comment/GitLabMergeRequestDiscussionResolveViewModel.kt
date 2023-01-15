// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.comment

import com.intellij.util.childScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabDiscussion
import java.util.*

interface GitLabMergeRequestDiscussionResolveViewModel {
  val resolved: Flow<Boolean>
  val busy: Flow<Boolean>

  fun changeResolvedState()
}

class GitLabMergeRequestDiscussionResolveViewModelImpl(parentCs: CoroutineScope, private val discussion: GitLabDiscussion)
  : GitLabMergeRequestDiscussionResolveViewModel {
  private val cs = parentCs.childScope()

  override val resolved: Flow<Boolean> = discussion.resolved

  private val currentTaskKey = MutableStateFlow<UUID?>(null)
  override val busy: Flow<Boolean> = currentTaskKey.map { it !== null }

  override fun changeResolvedState() {
    cs.launch {
      val key = UUID.randomUUID()
      // other task in progress
      if (!currentTaskKey.compareAndSet(null, key)) return@launch
      try {
        discussion.changeResolvedState()
      }
      catch (e: Exception) {
        if (e is CancellationException) throw e
        //TODO: handle???
      }
      finally {
        currentTaskKey.compareAndSet(key, null)
      }
    }
  }
}
