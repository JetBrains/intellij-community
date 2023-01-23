// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.gitlab.api.dto.GitLabMergeRequestDTO

internal interface GitLabMergeRequest {
  val title: Flow<String>
  val description: Flow<String>

  val number: String
  val url: String
}

internal class LoadedGitLabMergeRequest(mergeRequest: GitLabMergeRequestDTO) : GitLabMergeRequest {
  private val mergeRequestState: MutableStateFlow<GitLabMergeRequestDTO> = MutableStateFlow(mergeRequest)

  override val title: Flow<String> = mergeRequestState.map { it.title }
  override val description: Flow<String> = mergeRequestState.map { it.description }

  override val number: String = mergeRequest.iid
  override val url: String = mergeRequest.webUrl
}