// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest

internal interface GitLabMergeRequestDetailsInfoViewModel {
  val title: Flow<String>
  val description: Flow<String>
  val targetBranch: Flow<String>
  val sourceBranch: Flow<String>
  val hasConflicts: Flow<Boolean>

  val number: String
  val url: String
}

internal class GitLabMergeRequestDetailsInfoViewModelImpl(mergeRequest: GitLabMergeRequest) : GitLabMergeRequestDetailsInfoViewModel {
  override val title: Flow<String> = mergeRequest.title
  override val description: Flow<String> = mergeRequest.description
  override val targetBranch: Flow<String> = mergeRequest.targetBranch
  override val sourceBranch: Flow<String> = mergeRequest.sourceBranch
  override val hasConflicts: Flow<Boolean> = mergeRequest.hasConflicts

  override val number: String = mergeRequest.number
  override val url: String = mergeRequest.url
}