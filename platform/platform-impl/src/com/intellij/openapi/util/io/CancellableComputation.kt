// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io

import com.intellij.openapi.progress.CeProcessCanceledException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.isInCancellableContext
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.CancellableComputation.Companion.computeCancellable
import com.intellij.util.NotNullizer
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.function.Function

/**
 * Cancellable wrapper for (potentially) _non-cancellable computation_: computes a (potentially) long/hanging function
 * on a pooled worker, a wait for a result in a cancellable way.
 *
 * The difference with regular cancellable call is that the computation itself may be non-cooperating in regards with
 * the cancellation: i.e. the computation may _ignore_ the cancellation request, but the wrapper provides cancellability
 * anyway.
 *
 * Wrapper implements that by 'detaching' the slow/hanging/non-cancellable computation, and leaving it running in a
 * background. The detached computation wastes resources -- that is the price for non-cooperative behavior.
 *
 * IO, native calls, and interaction with external processes are the main examples of such potentially-non-cancellable
 * computations -- the class is designed with them in mind.
 *
 * An instance of the class should be used for performing _multiple_ similar operations; for one-shot computations,
 * static [computeCancellable] is cheaper and simpler to use.
 *
 * Implementation details:
 *
 * **Calls coalescing**: calls with same argument, `f(a)`, are coalesced -- if `f(a)` computation is already started,
 * subsequent calls don't initiate multiple parallel computations, but wait for the result of the first computation
 * started. But if the `f(a)` computation is already finished -- calling `f(a)` again _will_ initiate a new
 * computation.
 *
 * The coalescing is added to prevent a scenario there multiple identical long-running (potentially hanging)
 * computations are initiated because of (initiate->cancel->re-initiate) loop. If current computation `f(a)`
 * is running long by some reason -- it is unlikely that parallel instance of the same `f(a)` computation will
 * run faster -- it is possible, but unlikely. Much more likely -- as IO is concerned -- that running more
 * parallel `f(a)` computations will lead to longer and longer running time, because IO usually doesn't scale
 * very well.
 *
 * A side effect of this coalescing is **'spurious cancellation'**: if >1 `f(a)` requests are initiated in parallel,
 * and any of them gets cancelled -- all others get cancelled as well, because only 1 real computation is running,
 * and it gets cancelled. But even more: since the actual computation may not respond to cancellation request, and
 * thus continue running in background -- future `f(a)` requests will join waiting the same computation result, and
 * get cancelled immediately, until the actual computation terminates.
 *
 * This may be surprising behavior, but it contributes to the same goal: avoid running multiple instances of the
 * same (potentially heavy/long/hanging) computation in parallel.
 *
 * This coalescing is an **implementation detail**: it should be taken into account, but not relied upon, because
 * details could change over time.
 *
 * @param computation potentially non-cancellable computation to wrap. Computation results should be ready for
 * concurrent access, i.e. preferably thread-safe.
 * To avoid deadlocks, please pay attention to locks held at the call time and try to abstain from taking locks
 * inside the function.
 */
@Internal
fun <In, Out> wrapCancellable(computation: suspend (In) -> Out): suspend (In) -> Out {
  @OptIn(IntellijInternalApi::class, DelicateCoroutinesApi::class)
  @Suppress("SSBasedInspection")
  val coalescingAsyncComputation = CoroutineScope(blockingDispatcher + CoroutineName("detachedComputation: $computation"))
    .coalescing<In, Out> {
      computation(it)
    }
  return { arg ->
    coalescingAsyncComputation(arg)
  }
}

/**
 * Adapter to use [wrapCancellable] from java, replacement for [com.intellij.openapi.vfs.DiskQueryRelay].
 * For specification/implementation details see [wrapCancellable] docs
 */
//TODO RC: re-implement DiskQueryRelay with delegation to this class.
@Internal
class CancellableComputation<Param, Result>
private constructor(private val computation: Function<in Param, out Result>) : Function<Param, Result> {

  private val coalescingAsyncComputation = wrapCancellable<Param, Result> {
    computation.apply(it)
  }

  override fun apply(arg: Param): Result {
    if (!isInCancellableContext()) {
      //This branch is an optimization: we could run the computation through a regular async pipeline in all
      // the cases, but in a non-cancellable context this creates a useless overhead that we'd like to avoid.
      // That violates coalescing behavior: now it is possible for >1 identical computation to run in parallel.
      // Coalescing could be restored by registering this computation in the coalescer, but this requires richer
      // coalescer api -- will see is there a demand for that.
      return computation.apply(arg)
    }
    return runBlockingCancellable {
      try {
        coalescingAsyncComputation(arg)
      }
      catch (e: CancellationException) {
        //java clients probably expect PCE, not CE
        throw CeProcessCanceledException(e)
      }
    }
  }

  companion object {
    @JvmStatic
    fun <Param, Result> wrapAsCancellable(computation: Function<in Param, out Result>): Function<Param, Result> {
      return CancellableComputation(computation)
    }

    /**
     * Use the method for one-shot tasks; for performing multiple similar operations, prefer an instance of the class.
     *
     * To avoid deadlocks, please pay attention to locks held at the call time and try to abstain from taking locks
     * inside the `task` block.
     */
    @JvmStatic
    @Throws(ProcessCanceledException::class)
    fun <Result, E : Exception> computeCancellable(task: ThrowableComputable<Result, E>): Result {
      if (!isInCancellableContext()) {
        //optimization: skip offloading the task to another worker if cancellation is not needed anyway
        return task.compute()
      }

      return runBlockingCancellable {
        try {
          @Suppress("SSBasedInspection")
          @OptIn(IntellijInternalApi::class, DelicateCoroutinesApi::class)
          CoroutineScope(blockingDispatcher + CoroutineName("detachedComputation: $task")).async {
            task.compute()
          }.await()
        }
        catch (e: CancellationException) {
          //java clients probably expect PCE, not CE
          throw CeProcessCanceledException(e)
        }
      }
    }
  }

}


private val NOT_NULLIZER = NotNullizer("coalescing null")

/**
 * Creates a coalescing version of a given asynchronous computation.
 *
 * Returned function runs the wrapped computation asynchronously, in the context of current coroutine scope,
 * but coalesces the subsequent `f(a)` calls, while one `f(a)` is still running: if 2nd or following calls
 * _with the same argument_ come before first call finishes -- all those calls join waiting for a result
 * of the already initiated computation.
 *
 * Such coalescing pays off for long-running, resource-consuming computations.
 *
 * For coalescing to work properly, `In` should have `equals`/`hashCode` properly defined, and better should
 * be immutable value-type.
 */
internal fun <In, Out> CoroutineScope.coalescing(function: suspend (In) -> Out): suspend (In) -> Out {
  val cs = this@coalescing
  val computationsInProgress: ConcurrentMap<In & Any, Deferred<Out>> = ConcurrentHashMap()
  return { arg: In ->
    val nonNullArg = NOT_NULLIZER.notNullize(arg)
    val deferred = computationsInProgress.computeIfAbsent(nonNullArg) { nonNullArg ->
      @Suppress("UNCHECKED_CAST")
      val originalArg = NOT_NULLIZER.nullize(nonNullArg) as In
      cs.async {
        try {
          function(originalArg)
        }
        finally {
          computationsInProgress.remove(nonNullArg)
        }
      }
    }
    if (deferred.isCompleted) {
      //There is a time window between (the computation is initiated), and (Deferred is put into computationsInProgress).
      // If the computation finishes inside that window, then computationsInProgress.remove(eachArg) in a finally block
      // above runs uselessly, since there is nothing to remove yet -- and already completed Deferred will be put
      // into computationsInProgress later, even though it is no longer needed. Try to compensate for that here:
      computationsInProgress.remove(nonNullArg)
      //(we could solve it also with async(LAZY) + deferred.start(), but this delays start of the computation, thus
      // slightly reducing possible parallelism)
    }
    deferred.await()
  }
}


