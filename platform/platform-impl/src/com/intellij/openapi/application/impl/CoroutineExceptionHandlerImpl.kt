// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.ide.plugins.StartupAbortedException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * This is loaded using [java.util.ServiceLoader] and invoked by the Kotlin Coroutines machinery
 * to handle any uncaught exceptions thrown by coroutines launched in the [kotlinx.coroutines.GlobalScope].
 */
class CoroutineExceptionHandlerImpl : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {
  override fun handleException(context: CoroutineContext, exception: Throwable) {
    StartupAbortedException.processException(exception)
  }
}
