// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import org.jetbrains.plugins.gitlab.api.data.GitLabSnippetBlobActionEnum

data class GitLabSnippetBlobAction(
  val action: GitLabSnippetBlobActionEnum,
  val content: String?,
  val filePath: String,
  val previousPath: String?
)