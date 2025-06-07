// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.plugins.gitlab.api.data.GitLabPlan

class GitLabNamespaceRestDTO(
  @JsonProperty("plan") _plan: String
) {
  val plan: GitLabPlan = parseGitLabPlan(_plan)

  private fun parseGitLabPlan(errorText: String): GitLabPlan = try {
    GitLabPlan.valueOf(errorText.uppercase())
  }
  catch (_: Throwable) {
    GitLabPlan.FREE
  }
}