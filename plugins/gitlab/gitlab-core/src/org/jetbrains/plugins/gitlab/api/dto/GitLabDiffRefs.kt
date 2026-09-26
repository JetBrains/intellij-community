// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import org.jetbrains.plugins.gitlab.api.SinceGitLab

@SinceGitLab("12.1")
data class GitLabDiffRefs(
  val baseSha: String?,
  val headSha: String,
  val startSha: String
)