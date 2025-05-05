// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class SeLog {
  DEFAULT,
  ITEM_EMIT,
  USER_ACTION,
  LIFE_CYCLE,
  FROZEN_COUNT;

  companion object {
    private val allowedCategories = setOf(
      DEFAULT,
      ITEM_EMIT,
      USER_ACTION,
      LIFE_CYCLE,
      FROZEN_COUNT
    )

    // #com.intellij.platform.searchEverywhere.providers.SeLog
    private val logger = Logger.getInstance(SeLog::class.java)

    fun log(category: SeLog = DEFAULT, message: String) {
      if (!logger.isDebugEnabled || category !in allowedCategories) return

      logger.debug(message.withSePrefix(category))
    }

    fun log(category: SeLog = DEFAULT, messageProvider: () -> String) {
      if (!logger.isDebugEnabled || category !in allowedCategories) return

      logger.debug(messageProvider().withSePrefix(category))
    }

    suspend fun logSuspendable(category: SeLog = DEFAULT, messageProvider: suspend () -> String) {
      if (!logger.isDebugEnabled || category !in allowedCategories) return

      logger.debug(messageProvider().withSePrefix(category))
    }

    private fun String.withSePrefix(category: SeLog): String = "SearchEverywhere2 ($category): $this"
  }
}