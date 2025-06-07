// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import java.util.*

data class GitLabCommitRestDTO(
  val id: String,
  val shortId: String,
  val title: String,
  val authorEmail: String,
  val authorName: String,
  val createdAt: Date,
  val message: String,
)