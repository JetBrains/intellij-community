// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.dto

import org.jetbrains.plugins.gitlab.api.dto.GitLabProjectDTO

data class GitLabMergeRequestByBranchDTO(
  val iid: String,
  val targetBranch: String,
  val sourceBranch: String,
  val targetProject: GitLabProjectDTO,
  val sourceProject: GitLabProjectDTO?,
)
