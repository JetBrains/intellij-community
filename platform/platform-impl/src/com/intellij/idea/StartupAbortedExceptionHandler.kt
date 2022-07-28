// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea

import com.intellij.ide.plugins.StartupAbortedException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal object StartupAbortedExceptionHandler :
  AbstractCoroutineContextElement(CoroutineExceptionHandler),
  CoroutineExceptionHandler {

  override fun handleException(context: CoroutineContext, exception: Throwable) {
    StartupAbortedException.processException(exception)
  }

  override fun toString(): String = "StartupAbortedExceptionHandler"
}
