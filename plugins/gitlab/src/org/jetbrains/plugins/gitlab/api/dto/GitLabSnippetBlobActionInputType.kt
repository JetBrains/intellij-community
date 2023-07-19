// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import org.jetbrains.plugins.gitlab.api.data.GitLabSnippetBlobAction

data class GitLabSnippetBlobActionInputType(
  val action: GitLabSnippetBlobAction,
  val content: String?,
  val filePath: String,
  val previousPath: String?
)