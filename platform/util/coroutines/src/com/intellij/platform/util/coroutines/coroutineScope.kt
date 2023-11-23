// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.coroutines

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
  return ChildScope(coroutineContext + context, supervisor)
}

@Internal
fun CoroutineScope.namedChildScope(
  name: String,
  context: CoroutineContext = EmptyCoroutineContext,
  supervisor: Boolean = true,
): CoroutineScope {
  requireNoJob(context)
  return ChildScope(coroutineContext + context + CoroutineName(name), supervisor)
}

/**
 * This allows to see actual coroutine context (and name!)
 * in the coroutine dump instead of "SupervisorJobImpl{Active}@598294b2".
 *
 * [See issue](https://github.com/Kotlin/kotlinx.coroutines/issues/3428)
 */
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")
private class ChildScope(ctx: CoroutineContext, private val supervisor: Boolean) : JobImpl(ctx[Job]), CoroutineScope {

  override fun childCancelled(cause: Throwable): Boolean {
    return !supervisor && super.childCancelled(cause)
  }

  override val coroutineContext: CoroutineContext = ctx + this

  override fun toString(): String {
    val coroutineName = coroutineContext[CoroutineName]?.name
    return (if (coroutineName != null) "\"$coroutineName\":" else "") +
           (if (supervisor) "supervisor:" else "") +
           super.toString()
  }
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
@Suppress("DEPRECATION_ERROR")
fun CoroutineScope.attachAsChildTo(secondaryParent: CoroutineScope) {
  val parentJob = secondaryParent.coroutineContext.job
  val childJob = this.coroutineContext.job
  // prevent parent completion while child is not completed
  // propagate cancellation from parent to child
  val handle = (parentJob as JobSupport).attachChild(childJob as ChildJob)
  // propagate cancellation from child to parent
  childJob.invokeOnCompletion(onCancelling = true) { throwable ->
    if (throwable != null) {
      parentJob.childCancelled(throwable)
    }
  }
  childJob.invokeOnCompletion {
    handle.dispose() // remove reference from parent to child
  }
}