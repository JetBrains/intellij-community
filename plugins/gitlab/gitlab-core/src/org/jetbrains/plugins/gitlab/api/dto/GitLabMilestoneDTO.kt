// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import org.jetbrains.annotations.Nls

data class GitLabMilestoneDTO(
  val createdAt: String,
  val description: String?,
  val dueDate: String?,
  val id: Int,
  val iid: Int,
  val projectId: Int,
  val startDate: String?,
  val state: String,
  val title: @Nls String,
  val updatedAt: String,
  val webUrl: String
)