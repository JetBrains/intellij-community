// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

private val LOG: Logger
  get() = logger<CoroutineExceptionHandlerImpl>()

/**
 * This is loaded using [java.util.ServiceLoader] and invoked by the Kotlin Coroutines machinery
 * to handle any uncaught exceptions thrown by coroutines launched in the [kotlinx.coroutines.GlobalScope].
 */
class CoroutineExceptionHandlerImpl : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {

  override fun handleException(context: CoroutineContext, exception: Throwable) {
    if (exception is ProcessCanceledException && LoadingState.APP_STARTED.isOccurred && Registry.`is`("ide.log.coroutine.pce")) {
      runCatching {
        LOG.error(
          "Unhandled PCE in $context. " +
          "Try wrapping the throwing code into `blockingContext {}`",
          IllegalStateException(exception)
        )
      }
      return
    }
    try {
      LOG.error("Unhandled exception in $context", exception)
    }
    catch (ignored: Throwable) {
    }
  }
}
