// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import org.jetbrains.plugins.gitlab.api.GitLabRestId
import org.jetbrains.plugins.gitlab.api.GitLabRestIdData
import org.jetbrains.plugins.gitlab.api.SinceGitLab

@SinceGitLab("8.9")
data class GitLabAwardEmojiRestDTO(
  @JsonProperty("id")
  private val _id: String,
  val name: String,
  val user: GitLabUserRestDTO,
) {
  @JsonIgnore
  val id: GitLabRestId = GitLabRestIdData(_id)
}
