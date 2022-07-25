// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
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
