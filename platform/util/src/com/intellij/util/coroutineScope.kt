// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Internal
fun requireNoJob(context: CoroutineContext) {
  require(context[Job] == null) {
    "Context must not specify a Job: $context"
  }
}

@Internal
@Experimental
fun CoroutineScope.childScope(context: CoroutineContext = EmptyCoroutineContext, supervisor: Boolean = true): CoroutineScope {
  requireNoJob(context)
  val parentContext = coroutineContext
  val parentJob = parentContext.job
  val job = if (supervisor) SupervisorJob(parent = parentJob) else Job(parent = parentJob)
  return CoroutineScope(parentContext + job + context)
}

@Internal
fun CoroutineScope.namedChildScope(
  name: String,
  context: CoroutineContext = EmptyCoroutineContext,
  supervisor: Boolean = true,
): CoroutineScope {
  requireNoJob(context)
  // `launch` allows to see actual coroutine with its context (and name!)
  // in the coroutine dump instead of "SupervisorJobImpl{Active}@598294b2"
  // https://github.com/Kotlin/kotlinx.coroutines/issues/3428
  lateinit var result: CoroutineScope
  launch(context + CoroutineName(name), start = CoroutineStart.UNDISPATCHED) {
    if (supervisor) {
      supervisorScope {
        result = this@supervisorScope
        awaitCancellation() // keep it alive
      }
    }
    else {
      result = this@launch
      awaitCancellation() // keep it alive
    }
  }
  return result
}

/**
 * Makes [this] scope job behave as if it was a child of [secondaryParent] job
 * as per the coroutine framework parent-child [Job] relation:
 * - child prevents completion of the parent;
 * - child failure is propagated to the parent (note, parent might not cancel itself if it's a supervisor);
 * - parent cancellation is propagated to the child.
 *
 * The [real parent][ChildHandle.parent] of [this] scope job is not changed.
 * It does not matter whether the job of [this] scope has a real parent.
 *
 * Example usage:
 * ```
 * val containerScope: CoroutineScope = ...
 * val pluginScope: CoroutineScope = ...
 * CoroutineScope(SupervisorJob()).also {
 *   it.attachAsChildTo(containerScope)
 *   it.attachAsChildTo(pluginScope)
 * }
 * ```
 */
@Internal
@OptIn(InternalCoroutinesApi::class)
fun CoroutineScope.attachAsChildTo(secondaryParent: CoroutineScope) {
  val parentJob = secondaryParent.coroutineContext.job
  val childJob = this.coroutineContext.job

  // Prevent parent completion while child is not completed.
  secondaryParent.launch(
    CoroutineName("child handle '${coroutineContext[CoroutineName]?.name}'") +
    Dispatchers.Default,
    start = CoroutineStart.UNDISPATCHED,
  ) {
    withContext(NonCancellable) {
      childJob.join()
    }
  }

  // propagate cancellation from parent to child
  val handle = parentJob.invokeOnCompletion(onCancelling = true) { throwable ->
    if (throwable != null) {
      @Suppress("DEPRECATION_ERROR")
      (childJob as ChildJob).parentCancelled(parentJob as ParentJob)
    }
  }

  // propagate cancellation from child to parent
  childJob.invokeOnCompletion(onCancelling = true) { throwable ->
    handle.dispose() // remove reference from parent to child
    if (throwable != null) {
      @Suppress("DEPRECATION_ERROR")
      (parentJob as JobSupport).childCancelled(throwable)
    }
  }
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
