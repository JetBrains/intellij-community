// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("AppJavaExecutorUtil")

package com.intellij.util.concurrency

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.blockingContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Only for java clients and only if you cannot rewrite in Kotlin and use coroutines (as you should).
 */
@Internal
fun executeOnPooledIoThread(task: Runnable) {
  @Suppress("DEPRECATION")
  ApplicationManager.getApplication().coroutineScope.launch(Dispatchers.IO) {
    blockingContext(task::run)
  }
}