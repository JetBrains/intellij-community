// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.dto

import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import java.util.*

data class GitLabMergeRequestShortDTO(
  val id: Long,
  val projectId: Long,
  val title: String,
  val description: String,
  val state: String,
  val mergeStatus: String,
  val author: GitLabUserDTO,
  val assignees: List<GitLabUserDTO>,
  val reviewers: List<GitLabUserDTO>,
  val labels: List<String>,
  val createdAt: Date,
  val draft: Boolean,
  val webUrl: String
)