// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

data class GitLabRepositoryCreationRestDTO(
  val sshUrlToRepo: String,
  val httpUrlToRepo: String,
  val webUrl: String,
)