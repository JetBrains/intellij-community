// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import java.util.*

data class GitLabResourceStateEventDTO(
  val createdAt: Date,
  val id: Int,
  val resourceId: Int,
  val resourceType: String,
  val state: String,
  val user: GitLabUserDTO
)