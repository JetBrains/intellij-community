// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Execute coroutine on pooled thread. Uncaught error will be logged.
 *
 * @see com.intellij.openapi.application.Application.executeOnPooledThread
 */
@Deprecated(message = "Use Dispatchers.IO", replaceWith = ReplaceWith("Dispatchers.IO"))
@Suppress("unused") // unused receiver
val Dispatchers.ApplicationThreadPool: CoroutineDispatcher
  get() = IO
