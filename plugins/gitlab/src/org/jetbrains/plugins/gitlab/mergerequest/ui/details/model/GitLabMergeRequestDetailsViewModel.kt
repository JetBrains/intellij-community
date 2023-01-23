// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest

internal interface GitLabMergeRequestDetailsViewModel {
  val titleState: Flow<String>
  val descriptionState: Flow<String>

  val number: String
  val url: String
}

internal class GitLabMergeRequestDetailsViewModelImpl(mergeRequest: GitLabMergeRequest) : GitLabMergeRequestDetailsViewModel {
  override val titleState: Flow<String> = mergeRequest.title
  override val descriptionState: Flow<String> = mergeRequest.description

  override val number: String = mergeRequest.number
  override val url: String = mergeRequest.url
}