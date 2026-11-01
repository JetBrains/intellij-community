// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import org.jetbrains.plugins.gitlab.api.SinceGitLab

@SinceGitLab("12.0")
data class GitLabProjectIsForkedDTO(
  private val forkedFromProject: Unit?
) {
  @JsonIgnore
  val isForked: Boolean = forkedFromProject != null
}