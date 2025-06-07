// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabCommit
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.ui.GitLabUIUtil

@ApiStatus.Internal
class GitLabCommitViewModel(
  private val project: Project,
  private val mr: GitLabMergeRequest,
  model: GitLabCommit
) {
  internal val sha = model.sha
  internal val shortId = model.shortId

  internal val author = model.author?.name ?: model.authorName
  internal val authoredDate = model.authoredDate

  internal val titleHtml = model.fullTitle?.let {
    GitLabUIUtil.convertToHtml(project, mr.gitRepository, mr.glProject.projectPath, it)
  }
  internal val descriptionHtml = model.description?.removePrefix(model.fullTitle.orEmpty())?.let {
    GitLabUIUtil.convertToHtml(project, mr.gitRepository, mr.glProject.projectPath, it)
  }
}