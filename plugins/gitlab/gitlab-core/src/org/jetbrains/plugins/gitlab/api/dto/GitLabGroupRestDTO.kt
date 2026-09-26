// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.gitlab.api.SinceGitLab

@SinceGitLab("14.0", "No exact version")
class GitLabGroupRestDTO(
  val id: String,
  val path: String,
  val name: @NlsSafe String,
)