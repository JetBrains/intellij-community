// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.constraints

import com.intellij.openapi.Disposable
import com.intellij.openapi.WeakReferenceDisposableWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.util.IncorrectOperationException
import kotlinx.coroutines.*

/**
 * Capable of invoking a handler whenever something expires -
 * either a Disposable (see [DisposableExpiration]), or another job (see [JobExpiration]).
 */
interface Expiration {
  /**
   * Tells whether the handle has expired *and* every expiration handler has finished.
   * Returns false when checked from inside an expiration handler.
   */
  val isExpired: Boolean

  /** The caller must ensure the returned handle is properly disposed. */
  fun invokeOnExpiration(handler: Runnable): Handle

  interface Handle {
    fun unregisterHandler()
  }

  companion object  // to add extension functions
}

/**
 * Expiration backed by a [Job] instance.
 *
 * A Job is easier to interact with because of using homogeneous Job API when using it to setup
 * coroutine cancellation, and w.r.t. its lifecycle and memory management. Using it also has
 * performance considerations: two lock-free Job.invokeOnCompletion calls vs. multiple
 * synchronized Disposer calls per each launched coroutine.
 */
abstract class AbstractExpiration : Expiration {
  /**
   * Does not have children or a parent. Unlike the regular parent-children job relation,
   * having coroutine jobs attached to the job of an Expiration instance doesn't imply waiting of any kind,
   * neither does coroutine cancellation affect the supervisor job state.
   */
  protected abstract val job: Job

  override val isExpired: Boolean
    get() = job.isCompleted

  @Suppress("EXPERIMENTAL_IS_NOT_ENABLED")
  @UseExperimental(InternalCoroutinesApi::class)
  override fun invokeOnExpiration(handler: Runnable): Expiration.Handle =
    job.invokeOnCompletion(onCancelling = true) { handler.run() }.toHandlerRegistration()

  companion object {
    internal fun DisposableHandle.toHandlerRegistration(): Expiration.Handle {
      return object : Expiration.Handle {
        override fun unregisterHandler() {
          dispose()
        }
      }
    }
  }
}

class JobExpiration(override val job: Job) : AbstractExpiration()

/**
 * DisposableExpiration isolates interactions with a Disposable and the Disposer, using
 * an expirable supervisor job that gets cancelled whenever the Disposable is disposed.
 *
 * The DisposableExpiration itself is a lightweight thing w.r.t. creating it until it's supervisor Job
 * is really used, because registering a child Disposable within the Disposer tree happens lazily.
 */
class DisposableExpiration(private val disposable: Disposable) : AbstractExpiration() {
  override val job by lazy {
    SupervisorJob().also { job ->
      disposable.cancelJobOnDisposal(job, weaklyReferencedJob = true)  // the job doesn't leak through Disposer
    }
  }

  override val isExpired: Boolean
    get() = job.isCompleted && disposable.isDisposed

  override fun equals(other: Any?): Boolean = other is DisposableExpiration && disposable === other.disposable
  override fun hashCode(): Int = System.identityHashCode(disposable)
}

fun Expiration.Companion.composeExpiration(expirationSet: Collection<Expiration>): Expiration? {
  return when (expirationSet.size) {
    0 -> null
    1 -> expirationSet.single()
    else -> {
      val job = SupervisorJob()
      expirationSet.forEach {
        it.cancelJobOnExpiration(job)
      }
      JobExpiration(job)
    }
  }
}

fun Expiration.invokeOnExpiration(block: () -> Unit) = invokeOnExpiration(Runnable(block))

fun Expiration.cancelJobOnExpiration(job: Job): Expiration.Handle {
  return invokeOnExpiration {
    job.cancel()
  }.also { registration ->
    job.invokeOnCompletion { registration.unregisterHandler() }
  }
}


internal val Disposable.isDisposed: Boolean
  get() = Disposer.isDisposed(this)
internal val Disposable.isDisposing: Boolean
  get() = Disposer.isDisposing(this)

private fun tryRegisterDisposable(parent: Disposable, child: Disposable): Boolean {
  if (!parent.isDisposing &&
      !parent.isDisposed) {
    try {
      Disposer.register(parent, child)
      return true
    }
    catch (ignore: IncorrectOperationException) {  // Sorry but Disposer.register() is inherently thread-unsafe
    }
  }
  return false
}

/**
 * NOTE: there may be a hard ref to the [job] in the returned handle.
 */
internal fun Disposable.cancelJobOnDisposal(job: Job,
                                            weaklyReferencedJob: Boolean = false): AutoCloseable {
  val runOnce = ExpirableConstrainedExecution.Companion.RunOnce()
  val child = Disposable {
    runOnce {
      job.cancel()
    }
  }
  val childRef =
    if (!weaklyReferencedJob) child
    else WeakReferenceDisposableWrapper(child)

  if (!tryRegisterDisposable(this, childRef)) {
    Disposer.dispose(childRef)  // runs disposableBlock()
    return AutoCloseable { }
  }
  else {
    val completionHandler = object : CompletionHandler {
      @Suppress("unused")
      val hardRefToChild = child  // transitive: job -> completionHandler -> child

      override fun invoke(cause: Throwable?) {
        runOnce {
          Disposer.dispose(childRef)  // unregisters only, does not run disposableBlock()
        }
      }
    }
    val jobCompletionUnregisteringHandle = job.invokeOnCompletion(completionHandler)
    return AutoCloseable {
      jobCompletionUnregisteringHandle.dispose()
      completionHandler(null)
    }
  }
}
