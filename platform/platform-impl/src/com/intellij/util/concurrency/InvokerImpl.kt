// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.openapi.Disposable
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise

internal interface InvokerImpl : Disposable {
  val description: String
  fun offer(runnable: Runnable, delay: Int, promise: Promise<*>)
  fun run(task: Runnable, promise: AsyncPromise<*>): Boolean
}
