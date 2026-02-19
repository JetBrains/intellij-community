// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

private val LOG: Logger
  get() = logger<CoroutineExceptionHandlerImpl>()

/**
 * This is loaded using [java.util.ServiceLoader] and invoked by the Kotlin Coroutines machinery
 * to handle any uncaught exceptions thrown by coroutines launched in the [kotlinx.coroutines.GlobalScope].
 */
@ApiStatus.Internal
class CoroutineExceptionHandlerImpl : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {

  override fun handleException(context: CoroutineContext, exception: Throwable) {
    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    val effectiveContext = context
      .minusKey(kotlinx.coroutines.CoroutineId) // unstable: prevents persistent Throwable hash for reporting on TC
      .minusKey(Job) // contains `CoroutineId` as well
    processUnhandledException(exception, effectiveContext)
  }
}