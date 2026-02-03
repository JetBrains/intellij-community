// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabCommit
import org.jetbrains.plugins.gitlab.ui.GitLabMarkdownToHtmlConverter


@ApiStatus.Internal
class GitLabCommitViewModel(
  model: GitLabCommit,
  private val htmlConverter: GitLabMarkdownToHtmlConverter
) {
  internal val sha = model.sha
  internal val shortId = model.shortId

  internal val author = model.author?.name ?: model.authorName
  internal val authoredDate = model.authoredDate

  internal val titleHtml = model.fullTitle?.let {
    htmlConverter.convertToHtml(it)
  }
  internal val descriptionHtml = model.description?.removePrefix(model.fullTitle.orEmpty())?.let {
    htmlConverter.convertToHtml(it)
  }
}