// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object SeLog {
  const val DEFAULT: Int = 0
  const val ITEM_EMIT: Int = 1
  const val USER_ACTION: Int = 2

  private val allowedCategories = setOf(DEFAULT, ITEM_EMIT, USER_ACTION)

  // #com.intellij.platform.searchEverywhere.providers.SeLog
  private val logger = Logger.getInstance(SeLog::class.java)

  fun log(category: Int = DEFAULT, message: String) {
    if (!logger.isDebugEnabled || category !in allowedCategories) return

    logger.debug(message.withSePrefix())
  }

  fun log(category: Int = DEFAULT, messageProvider: () -> String) {
    if (!logger.isDebugEnabled || category !in allowedCategories) return

    logger.debug(messageProvider().withSePrefix())
  }

  suspend fun logSuspendable(category: Int = DEFAULT, messageProvider: suspend () -> String) {
    if (!logger.isDebugEnabled || category !in allowedCategories) return

    logger.debug(messageProvider().withSePrefix())
  }

  private fun String.withSePrefix(): String = "SearchEverywhere2: $this"
}