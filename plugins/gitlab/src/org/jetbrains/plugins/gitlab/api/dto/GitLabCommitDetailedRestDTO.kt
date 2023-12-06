// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

data class GitLabCommitDetailedRestDTO(
  val authorEmail: String,
  val authorName: String,
  val authoredDate: String,
  val committedDate: String,
  val committerEmail: String,
  val committerName: String,
  val createdAt: String,
  val id: String,
  val lastPipeline: Pipeline?,
  val message: String,
  val parentIds: List<String>,
  val shortId: String,
  val status: String?,
  val title: String,
  val webUrl: String
) {
  data class Pipeline(
    val id: Int,
    val ref: String,
    val sha: String,
    val status: String
  )
}