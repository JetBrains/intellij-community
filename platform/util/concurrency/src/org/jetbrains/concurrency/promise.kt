// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("Promises")
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.concurrency

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.ActionCallback
import com.intellij.util.Function
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BiConsumer
import java.util.function.Consumer

private val obsoleteError: RuntimeException by lazy { MessageError("Obsolete", false) }

val Promise<*>.isRejected: Boolean
  get() = state == Promise.State.REJECTED

val Promise<*>.isPending: Boolean
  get() = state == Promise.State.PENDING

private val REJECTED: Promise<*> by lazy {
  DonePromise<Any?>(PromiseValue.createRejected(createError("rejected")))
}

@Suppress("RemoveExplicitTypeArguments")
private val fulfilledPromise: CancellablePromise<Any?> by lazy {
  DonePromise<Any?>(PromiseValue.createFulfilled(null))
}

@Suppress("UNCHECKED_CAST")
fun <T> resolvedPromise(): Promise<T> = fulfilledPromise as Promise<T>

fun nullPromise(): Promise<*> = fulfilledPromise

/**
 * Creates a promise that is resolved with the given value.
 */
fun <T> resolvedPromise(result: T): Promise<T> = resolvedCancellablePromise(result)

/**
 * Create a promise that is resolved with the given value.
 */
fun <T> resolvedCancellablePromise(result: T): CancellablePromise<T> {
  @Suppress("UNCHECKED_CAST")
  return when (result) {
    null -> fulfilledPromise as CancellablePromise<T>
    else -> DonePromise(PromiseValue.createFulfilled(result))
  }
}

@Suppress("UNCHECKED_CAST")
/**
 * Consider passing error.
 */
fun <T> rejectedPromise(): Promise<T> = REJECTED as Promise<T>

fun <T> rejectedPromise(error: String): Promise<T> = rejectedCancellablePromise(error)

fun <T> rejectedPromise(error: Throwable?): Promise<T> {
  @Suppress("UNCHECKED_CAST")
  return when (error) {
    null -> REJECTED as Promise<T>
    else -> DonePromise(PromiseValue.createRejected(error))
  }
}

fun <T> rejectedCancellablePromise(error: String): CancellablePromise<T> {
  return DonePromise(PromiseValue.createRejected(createError(error, true)))
}

@Suppress("RemoveExplicitTypeArguments")
private val CANCELLED_PROMISE: Promise<Any?> by lazy {
  DonePromise(PromiseValue.createRejected<Any?>(obsoleteError))
}

@Suppress("UNCHECKED_CAST")
fun <T> cancelledPromise(): Promise<T> = CANCELLED_PROMISE as Promise<T>


// only internal usage
interface ObsolescentFunction<Param, Result> : Function<Param, Result>, Obsolescent

abstract class ValueNodeAsyncFunction<PARAM, RESULT>(private val node: Obsolescent) : Function<PARAM, Promise<RESULT>>, Obsolescent {
  override fun isObsolete(): Boolean = node.isObsolete
}

abstract class ObsolescentConsumer<T>(private val obsolescent: Obsolescent) : Obsolescent, Consumer<T> {
  override fun isObsolete(): Boolean = obsolescent.isObsolete
}

inline fun <T, SUB_RESULT> Promise<T>.then(obsolescent: Obsolescent, crossinline handler: (T) -> SUB_RESULT): Promise<SUB_RESULT> = then(object : ObsolescentFunction<T, SUB_RESULT> {
  override fun `fun`(param: T) = handler(param)

  override fun isObsolete() = obsolescent.isObsolete
})


inline fun <T> Promise<T>.onSuccess(node: Obsolescent, crossinline handler: (T) -> Unit): Promise<T> = onSuccess(object : ObsolescentConsumer<T>(node) {
  override fun accept(param: T) = handler(param)
})

inline fun Promise<*>.processed(node: Obsolescent, crossinline handler: () -> Unit): Promise<Any?> {
  @Suppress("UNCHECKED_CAST")
  return (this as Promise<Any?>)
    .onProcessed(object : ObsolescentConsumer<Any?>(node) {
      override fun accept(param: Any?) = handler()
    })
}

@Suppress("UNCHECKED_CAST")
inline fun <T> Promise<*>.thenRun(crossinline handler: () -> T): Promise<T> = (this as Promise<Any?>).then { handler() }

@Suppress("UNCHECKED_CAST")
inline fun Promise<*>.processedRun(crossinline handler: () -> Unit): Promise<*> {
  return (this as Promise<Any?>).onProcessed { handler() }
}


inline fun <T, SUB_RESULT> Promise<T>.thenAsync(node: Obsolescent, crossinline handler: (T) -> Promise<SUB_RESULT>): Promise<SUB_RESULT> = thenAsync(object : ValueNodeAsyncFunction<T, SUB_RESULT>(node) {
  override fun `fun`(param: T) = handler(param)
})

@Suppress("UNCHECKED_CAST")
inline fun <T> Promise<T>.thenAsyncAccept(node: Obsolescent, crossinline handler: (T) -> Promise<*>): Promise<Any?> {
  return thenAsync(object : ValueNodeAsyncFunction<T, Any?>(node) {
    override fun `fun`(param: T) = handler(param) as Promise<Any?>
  })
}

inline fun <T> Promise<T>.thenAsyncAccept(crossinline handler: (T) -> Promise<*>): Promise<Any?> = thenAsync(Function { param ->
  @Suppress("UNCHECKED_CAST")
  (return@Function handler(param) as Promise<Any?>)
})


inline fun Promise<*>.onError(node: Obsolescent, crossinline handler: (Throwable) -> Unit): Promise<out Any> = onError(object : ObsolescentConsumer<Throwable>(node) {
  override fun accept(param: Throwable) = handler(param)
})

/**
 * Merge results into one list. Results are ordered as in the promises list.
 *
 * `T` here is a not nullable type, if you use this method from Java, take care that all promises are not resolved to `null`.
 *
 * If `ignoreErrors = false`, list of the same size is returned.
 * If `ignoreErrors = true`, list of different size is returned if some promise failed with error.
 */
@JvmOverloads
fun <T : Any> Collection<Promise<T>>.collectResults(ignoreErrors: Boolean = false): Promise<List<T>> {
  if (isEmpty()) {
    return resolvedPromise(emptyList())
  }

  val result = AsyncPromise<List<T>>()
  val latch = AtomicInteger(size)
  val list = Collections.synchronizedList(Collections.nCopies<T?>(size, null).toMutableList())

  fun arrive() {
    if (latch.decrementAndGet() == 0) {
      if (ignoreErrors) {
        list.removeIf { it == null }
      }
      result.setResult(list as List<T>)
    }
  }

  for ((i, promise) in this.withIndex()) {
    promise.onSuccess {
      list.set(i, it)
      arrive()
    }
    promise.onError {
      if (ignoreErrors) {
        arrive()
      }
      else {
        result.setError(it)
      }
    }
  }
  return result
}

@JvmOverloads
fun createError(error: String, log: Boolean = false): RuntimeException = MessageError(error, log)

inline fun <T> AsyncPromise<T>.compute(runnable: () -> T) {
  val result = try {
    runnable()
  }
  catch (e: Throwable) {
    setError(e)
    return
  }

  setResult(result)
}

inline fun <T> runAsync(crossinline runnable: () -> T): Promise<T> {
  val promise = AsyncPromise<T>()
  AppExecutorUtil.getAppExecutorService().execute {
    val result = try {
      runnable()
    }
    catch (e: Throwable) {
      promise.setError(e)
      return@execute
    }
    promise.setResult(result)
  }
  return promise
}

/**
 * Log error if not a message error
 */
fun Logger.errorIfNotMessage(e: Throwable): Boolean {
  if (e is MessageError) {
    val log = e.log
    if (log == ThreeState.YES || (log == ThreeState.UNSURE && ApplicationManager.getApplication()?.isUnitTestMode == true)) {
      error(e)
      return true
    }
  }
  else if (e !is ControlFlowException && e !is CancellationException) {
    error(e)
    return true
  }

  return false
}

fun <T> CompletableFuture<T>.asPromise(): Promise<T> {
  val promise = AsyncPromise<T>()
  handle { result, throwable ->
    if (throwable == null) {
      promise.setResult(result)
    }
    else {
      promise.setError(throwable)
    }
    result
  }
  return promise
}

fun <T> CompletableFuture<T>.asCancellablePromise(): CancellablePromise<T> {
  val promise = AsyncPromise<T>()
  val future = this
  handle { result, throwable ->
    if (throwable == null) {
      promise.setResult(result)
    }
    else {
      promise.setError(throwable)
    }
    result
  }
  promise.f.handle { result, throwable ->
    if (throwable == AsyncPromise.CANCELED && !future.isDone) {
      future.completeExceptionally(throwable)
    }
    result
  }
  return promise
}

/**
 * @see [kotlinx.coroutines.future.asCompletableFuture]
 * @see [kotlinx.coroutines.future.setupCancellation]
 */
fun Job.asPromise(): Promise<*> {
  val promise = AsyncPromise<Any?>()

  promise.onError { throwable ->
    val cancellationException = throwable as? CancellationException
                                ?: CancellationException("Promise was completed exceptionally", throwable)
    cancel(cancellationException)
  }

  invokeOnCompletion { throwable ->
    if (throwable == null) {
      promise.setResult(null)
    }
    else {
      promise.setError(throwable)
    }
  }
  return promise
}

fun ActionCallback.toPromise(): Promise<Any?> {
  val promise = AsyncPromise<Any?>()
  doWhenDone { promise.setResult(null) }
    .doWhenRejected { error -> promise.setError(createError(error ?: "Internal error")) }
  return promise
}

fun Promise<*>.toActionCallback(): ActionCallback {
  val result = ActionCallback()
  onSuccess { result.setDone() }
  onError { result.setRejected() }
  return result
}

fun Collection<Promise<*>>.all(): Promise<*> = if (size == 1) first() else all(null)

/**
 * @see collectResults
 */
@JvmOverloads
fun <T: Any?> Collection<Promise<*>>.all(totalResult: T, ignoreErrors: Boolean = false): Promise<T> {
  if (isEmpty()) {
    return resolvedPromise()
  }

  val totalPromise = AsyncPromise<T>()
  val done = CountDownConsumer(size, totalPromise, totalResult)
  val rejected = if (ignoreErrors) {
    Consumer { done.accept(null) }
  }
  else {
    Consumer<Throwable> { totalPromise.setError(it) }
  }

  for (promise in this) {
    promise.onSuccess(done)
    promise.onError(rejected)
  }
  return totalPromise
}

private class CountDownConsumer<T : Any?>(countDown: Int, private val promise: AsyncPromise<T>, private val totalResult: T) : Consumer<Any?> {
  private val countDown = AtomicInteger(countDown)

  override fun accept(t: Any?) {
    if (countDown.decrementAndGet() == 0) {
      promise.setResult(totalResult)
    }
  }
}

fun <T> any(promises: Collection<Promise<T>>, totalError: String): Promise<T> {
  if (promises.isEmpty()) {
    return resolvedPromise()
  }
  else if (promises.size == 1) {
    return promises.first()
  }

  val totalPromise = AsyncPromise<T>()
  val done = Consumer<T> { result -> totalPromise.setResult(result) }
  val rejected = object : Consumer<Throwable> {
    private val toConsume = AtomicInteger(promises.size)

    override fun accept(throwable: Throwable) {
      if (toConsume.decrementAndGet() <= 0) {
        totalPromise.setError(totalError)
      }
    }
  }

  for (promise in promises) {
    promise.onSuccess(done)
    promise.onError(rejected)
  }
  return totalPromise
}


fun <T> Collection<Promise<T>>.waitAll(node: Obsolescent): Promise<List<T>> {
  if (isEmpty()) {
    return resolvedPromise(emptyList())
  }
  else if (size == 1) {
    return first().then(node, ::listOf)
  }

  val totalPromise = AsyncPromise<List<T>>()

  val done = object : BiConsumer<Int, T> {
    val toConsume = AtomicInteger(size)
    val result = Collections.synchronizedList(
      MutableList<T?>(size) { null }
    )

    override fun accept(index: Int, t: T) {
      result[index] = t
      if (toConsume.decrementAndGet() <= 0) {
        @Suppress("UNCHECKED_CAST")
        totalPromise.setResult(result as List<T>)
      }
    }
  }

  val rejected = Consumer<Throwable> { throwable -> totalPromise.setError(throwable) }

  for ((index, promise) in withIndex()) {
    promise.onSuccess(node) { done.accept(index, it) }
    promise.onError(node, rejected::accept)
  }

  return totalPromise
}

fun <T> Collection<T>.first(node: Obsolescent, predicate: (T) -> Promise<Boolean>): Promise<T?> {
  if (isEmpty()) {
    return resolvedPromise()
  }

  val totalPromise = AsyncPromise<T?>()
  val toConsume = AtomicInteger(size)

  val done = Consumer<T?> { value ->
    if (value != null) {
      totalPromise.setResult(value)
    } else if (toConsume.decrementAndGet() <= 0) {
      totalPromise.setResult(null)
    }
  }

  val rejected = Consumer<Throwable> { throwable ->
    if (toConsume.decrementAndGet() <= 0) {
      totalPromise.setError(throwable)
    }
  }

  for (element in this) {
    predicate(element)
      .then(node) { matched -> done.accept(element.takeIf { matched }) }
      .onError(node) { rejected.accept(it) }
  }

  return totalPromise
}

private class DonePromise<T>(private val value: PromiseValue<T>) : Promise<T>, Future<T>, CancellablePromise<T> {
  /**
   * The same as @{link Future[Future.isDone]}.
   * Completion may be due to normal termination, an exception, or cancellation -- in all of these cases, this method will return true.
   */
  override fun isDone() = true

  override fun getState() = value.state

  override fun isCancelled() = false

  override fun get() = blockingGet(-1)

  override fun get(timeout: Long, unit: TimeUnit) = blockingGet(timeout.toInt(), unit)

  override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
    if (state == Promise.State.PENDING) {
      cancel()
      return true
    }
    else {
      return false
    }
  }

  override fun onSuccess(handler: Consumer<in T?>): CancellablePromise<T> {
    if (value.error != null) {
      return this
    }

    if (!isHandlerObsolete(handler)) {
      handler.accept(value.result)
    }
    return this
  }

  @Suppress("UNCHECKED_CAST")
  override fun processed(child: Promise<in T?>): Promise<T> {
    if (child is CompletablePromise<*>) {
      if (value.error != null) {
        child.setError(value.error)
      }
      else {
        (child as CompletablePromise<T>).setResult(value.result)
      }
    }
    return this
  }

  override fun onProcessed(handler: Consumer<in T?>): CancellablePromise<T> {
    if (value.error == null) {
      onSuccess(handler)
    }
    else if (!isHandlerObsolete(handler)) {
      handler.accept(null)
    }
    return this
  }

  override fun onError(handler: Consumer<in Throwable?>): CancellablePromise<T> {
    if (value.error != null && !isHandlerObsolete(handler)) {
      handler.accept(value.error)
    }
    return this
  }

  override fun <SUB_RESULT : Any?> then(done: Function<in T, out SUB_RESULT>): Promise<SUB_RESULT> {
    @Suppress("UNCHECKED_CAST")
    return when {
      value.error != null -> this as Promise<SUB_RESULT>
      isHandlerObsolete(done) -> cancelledPromise()
      else -> DonePromise(PromiseValue.createFulfilled(done.`fun`(value.result)))
    }
  }

  override fun <SUB_RESULT : Any?> thenAsync(done: Function<in T, out Promise<SUB_RESULT>>): Promise<SUB_RESULT> {
    if (value.error == null) {
      return done.`fun`(value.result)
    }
    else {
      @Suppress("UNCHECKED_CAST")
      return this as Promise<SUB_RESULT>
    }
  }

  override fun blockingGet(timeout: Int, timeUnit: TimeUnit) = value.getResultOrThrowError()

  override fun cancel() {}
}