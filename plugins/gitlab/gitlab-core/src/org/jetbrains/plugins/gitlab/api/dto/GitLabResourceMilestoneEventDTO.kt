// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import java.util.*

data class GitLabResourceMilestoneEventDTO(
  val action: String,
  val createdAt: Date,
  val id: Int,
  val milestone: GitLabMilestoneDTO,
  val resourceId: Int,
  val resourceType: String,
  val user: GitLabUserDTO
) {
  val actionEnum: Action = parseAction(action)

  // unknown???
  enum class Action {
    ADD, REMOVE
  }

  companion object {
    private val logger: Logger = logger<GitLabResourceMilestoneEventDTO>()

    private fun parseAction(value: String): Action = try {
      Action.valueOf(value.uppercase(Locale.getDefault()))
    }
    catch (_: IllegalArgumentException) {
      logger.warn("Unable to parse merge label event action: $value")
      Action.ADD
    }
  }
}