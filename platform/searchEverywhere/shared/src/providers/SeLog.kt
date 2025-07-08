// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.cancellation.CancellationException

@ApiStatus.Internal
enum class SeLog {
  DEFAULT,
  ITEM_EMIT,
  USER_ACTION,
  LIFE_CYCLE,
  FROZEN_COUNT,
  THROTTLING,
  WARNING,
  EQUALITY;

  companion object {
    private val allowedCategories = setOf(
      DEFAULT,
      ITEM_EMIT,
      USER_ACTION,
      LIFE_CYCLE,
      FROZEN_COUNT,
      THROTTLING,
      WARNING,
      EQUALITY
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

    fun warn(message: String) {
      logger.warn(message.withSePrefix(WARNING))
    }

    fun error(message: String) {
      logger.error(message)
    }

    fun error(throwable: Throwable) {
      logger.error(throwable)
    }

    private fun String.withSePrefix(category: SeLog): String = "SearchEverywhere2 ($category): $this"
  }
}

@ApiStatus.Internal
suspend fun <T> computeCatchingOrNull(catchMessage: (Throwable) -> String, block: suspend () -> T?): T? =
  computeCatchingOrNull(muteLogExternally = false, catchMessage, block)

@ApiStatus.Internal
suspend fun <T> computeCatchingOrNull(muteLogExternally: Boolean, catchMessage: (Throwable) -> String, block: suspend () -> T?): T? =
  try {
    block()
  }
  catch (c: CancellationException) {
    throw c
  }
  catch (e: Throwable) {
    if (!muteLogExternally || ApplicationManager.getApplication().isInternal) {
      SeLog.warn(catchMessage(e))
    }
    null
  }
