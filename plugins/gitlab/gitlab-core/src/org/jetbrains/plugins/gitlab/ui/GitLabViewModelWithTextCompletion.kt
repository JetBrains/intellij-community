// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.ui

import com.intellij.collaboration.async.childScope
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.openapi.util.Key
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabProject

interface GitLabViewModelWithTextCompletion {

  fun withMentionCompletionModel(consumer: (GitLabTextCompletionViewModel) -> Unit)

  companion object {
    val MENTIONS_COMPLETION_KEY: Key<GitLabViewModelWithTextCompletion> = Key.create("GitLab.Chat.MentionsContextViewModel")
  }
}

internal class GitLabViewModelWithTextCompletionImpl(
  parentCs: CoroutineScope,
  private val projectData: GitLabProject,
  private val mergeRequest: GitLabMergeRequest,
  private val avatarIconsProvider: IconsProvider<GitLabUserDTO>,
) : GitLabViewModelWithTextCompletion {
  private val cs: CoroutineScope = parentCs.childScope(this::class)

  override fun withMentionCompletionModel(consumer: (GitLabTextCompletionViewModel) -> Unit) {
    val vmCs = cs.childScope(GitLabViewModelWithTextCompletionImpl::class.java.name)
    val vm = GitLabTextCompletionViewModelImpl(vmCs, avatarIconsProvider, projectData, mergeRequest)
    try {
      consumer(vm)
    }
    finally {
      vmCs.cancel()
    }
  }
}