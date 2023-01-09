// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Internal
@Experimental
fun CoroutineScope.childScope(context: CoroutineContext = EmptyCoroutineContext, supervisor: Boolean = true): CoroutineScope {
  require(context[Job] == null) {
    "Found Job in context: $context"
  }
  val parentContext = coroutineContext
  val parentJob = parentContext.job
  val job = if (supervisor) SupervisorJob(parent = parentJob) else Job(parent = parentJob)
  return CoroutineScope(parentContext + job + context)
}

@Internal
@Experimental
fun Job.cancelOnDispose(disposable: Disposable) {
  val childDisposable = Disposable { cancel("disposed") }
  Disposer.register(disposable, childDisposable)
  job.invokeOnCompletion {
    Disposer.dispose(childDisposable)
  }
}
