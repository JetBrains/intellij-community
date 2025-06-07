// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.openapi.util.NlsSafe

class GitLabUserRestDTO(
  val id: String,
  val username: String,
  val name: @NlsSafe String,
  val avatarUrl: String?,
  val webUrl: String
)