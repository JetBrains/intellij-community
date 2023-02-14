// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.lazyPub
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * This is loaded using [java.util.ServiceLoader] and invoked by the Kotlin Coroutines machinery
 * to handle any uncaught exceptions thrown by coroutines launched in the [kotlinx.coroutines.GlobalScope].
 */
class CoroutineExceptionHandlerImpl : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {

  override fun handleException(context: CoroutineContext, exception: Throwable) {
    if (exception is ProcessCanceledException) {
      return
    }
    try {
      LOG.error("Unhandled exception in $context", exception)
    }
    catch (ignored: Throwable) {
    }
  }

  companion object {
    private val LOG: Logger by lazyPub { Logger.getInstance(CoroutineExceptionHandlerImpl::class.java) }
  }
}
