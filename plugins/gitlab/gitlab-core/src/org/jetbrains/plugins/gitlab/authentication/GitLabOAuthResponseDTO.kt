// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.authentication

data class GitLabOAuthResponseDTO(
  val accessToken: String,
  val expiresIn: Int,
  val refreshToken: String,
  val createdAt: Long,
)
