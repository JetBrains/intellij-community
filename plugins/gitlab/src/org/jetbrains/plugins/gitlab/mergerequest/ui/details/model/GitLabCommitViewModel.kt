// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabCommit
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.ui.GitLabUIUtil

internal class GitLabCommitViewModel(
  private val project: Project,
  private val mr: GitLabMergeRequest,
  model: GitLabCommit
) {
  val sha = model.sha
  val shortId = model.shortId

  val author = model.author?.name ?: model.authorName
  val authoredDate = model.authoredDate

  val titleHtml = model.fullTitle?.let {
    GitLabUIUtil.convertToHtml(project, mr.gitRepository, mr.glProject.projectPath, it)
  }
  val descriptionHtml = model.description?.removePrefix(model.fullTitle.orEmpty())?.let {
    GitLabUIUtil.convertToHtml(project, mr.gitRepository, mr.glProject.projectPath, it)
  }
}