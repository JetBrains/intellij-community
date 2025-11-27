// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.gitlab.api.SinceGitLab

/**
 * Uploaded file data.
 *
 * @see <a href="https://docs.gitlab.com/api/project_markdown_uploads/">Markdown uploads API</a>
 */
@SinceGitLab("15.10")
class GitLabUploadRestDTO(
  @SinceGitLab("17.3") val id: String,
  val alt: @NlsSafe String,
  val url: @NlsSafe String,
  @SinceGitLab("17.1") val fullPath: @NlsSafe String,
  val markdown: @NlsSafe String,
)