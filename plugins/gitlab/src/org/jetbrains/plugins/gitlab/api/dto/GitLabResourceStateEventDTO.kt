// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import java.util.*

data class GitLabResourceStateEventDTO(
  val createdAt: Date,
  val id: Int,
  val resourceId: Int,
  val resourceType: String,
  val state: String,
  val user: GitLabUserDTO
) {
  val stateEnum: State = parseState(state)

  enum class State {
    CLOSED, REOPENED, MERGED
  }

  companion object {
    private val logger: Logger = logger<GitLabResourceLabelEventDTO>()

    private fun parseState(value: String): State = try {
      State.valueOf(value.uppercase(Locale.getDefault()))
    }
    catch (_: IllegalArgumentException) {
      logger.warn("Unable to parse merge label event action: $value")
      State.CLOSED
    }
  }
}