// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

class GitLabNamespaceRestDTO(
  @JsonProperty("plan") _plan: String
) {
  val plan: GitLabPlan = parseGitLabPlan(_plan)

  private fun parseGitLabPlan(textual: String): GitLabPlan = try {
    GitLabPlan.valueOf(textual.uppercase())
  }
  catch (_: Throwable) {
    GitLabPlan.FREE
  }
}