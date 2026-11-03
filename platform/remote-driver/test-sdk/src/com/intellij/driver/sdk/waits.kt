package com.intellij.driver.sdk

import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.printableString
import com.intellij.openapi.diagnostic.fileLogger
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

private val LOG get() = fileLogger()

/**
 * Waits for a condition to be met within a specified timeout period.
 *
 * To report the actual value on failure, use the `waitFor` overload with `getter` and `checker`.
 * It adds the last value to the error, so you do not need [errorMessage].
 *
 * @throws WaitForException if the condition is not met within the specified timeout
 *
 * @see WaitForException
 */
fun waitFor(
  message: String? = null,
  timeout: Duration = 5.seconds,
  interval: Duration = 1.seconds,
  errorMessage: (() -> String)? = null,
  condition: () -> Boolean,
) {
  waitFor(message = message,
          timeout = timeout,
          interval = interval,
          errorMessage = if (errorMessage == null) {
            null
          }
          else {
            run@{ errorMessage.invoke() }
          },
          getter = condition,
          checker = { it })
}

/**
 * A utility method that waits for a non-null value to be returned by the specified getter function.
 *
 * @return the non-null value returned by the getter function
 * @throws WaitForException if the timeout is reached and the getter function still returns a null value
 *
 * @see waitFor
 * @see logAwaitStart
 * @see WaitForException
 * @see logAwaitFinish
 */
fun <T> waitNotNull(
  message: String? = null,
  timeout: Duration = 5.seconds,
  interval: Duration = 1.seconds,
  errorMessage: ((T?) -> String)? = null,
  getter: () -> T?,
): T {
  return waitFor(message = message, timeout = timeout,
                 interval = interval,
                 errorMessage = errorMessage ?: { _ -> failure("Await not null", message, null) },
                 getter = getter,
                 checker = { it != null }
  )!!
}

/** See [waitNotNull] */
suspend fun <T> waitNotNullAsync(
  message: String? = null,
  timeout: Duration = 5.seconds,
  interval: Duration = 1.seconds,
  errorMessage: ((T?) -> String)? = null,
  getter: suspend () -> T?,
): T {
  return waitForAsync(
    message = message,
    timeout = timeout,
    interval = interval,
    errorMessage = errorMessage ?: { _ -> failure("Await not null", message, null) },
    getter = getter,
    checker = { it != null }
  )!!
}

/**
 * Waits for at least one element in the list that satisfies the provided conditions.
 *
 * @return a list of elements that satisfy the conditions
 */
fun <T> waitAny(
  message: String? = null,
  timeout: Duration = 5.seconds,
  interval: Duration = 1.seconds,
  errorMessage: ((List<T>) -> String)? = null,
  getter: () -> List<T>,
  checker: (T) -> Boolean = { true },
): List<T> {
  return waitFor(message = message, timeout = timeout,
                 interval = interval,
                 errorMessage = { (all, _, notSuitable) ->
                   errorMessage?.invoke(all)
                   ?: (failure("Await any", message, null) + section("not suitable", notSuitable))
                 },
                 getter = { observe(getter(), checker) },
                 checker = { it.suitable.isNotEmpty() }
  ).suitable
}

private data class Observation<C, T>(val all: C, val suitable: List<T>, val notSuitable: List<T>) {
  override fun toString(): String = "suitable: $suitable, not suitable: $notSuitable"
}

private fun <C : Collection<T>, T> observe(all: C, checker: (T) -> Boolean): Observation<C, T> {
  val (suitable, notSuitable) = all.partition(checker)
  return Observation(all, suitable, notSuitable)
}

private fun failure(await: String?, message: String?, details: String?): String {
  val header = listOfNotNull(message, await).joinToString(": ").ifEmpty { null }
  return listOfNotNull(header, details).joinToString(". ")
}

private fun section(label: String, elements: List<*>): String {
  return "\n\t$label:\n\t\t" + if (elements.isEmpty()) "none" else printableString(elements.joinToString("\n\t\t"))
}

private fun logAwaitStart(message: String?, timeout: Duration) {
  message?.let { LOG.info("Await: '$it' with timeout $timeout") }
}

private fun <T> logAwaitFinish(message: String?, result: T, passedTime: Duration) {
  message?.let {
    LOG.info("Await: '$it' resulted in $passedTime with \n\t${printableString(result.toString())}")
  }
}


/**
 * Waits for a condition to be met within a specified timeout period.
 *
 * @return the value retrieved by the getter function after the condition is met
 * @throws WaitForException if the condition is not met within the specified timeout
 *
 * @see logAwaitStart
 * @see WaitForException
 * @see logAwaitFinish
 */
fun <T> waitFor(
  message: String? = null,
  timeout: Duration = 5.seconds,
  interval: Duration = 1.seconds,
  errorMessage: ((T) -> String)? = null,
  getter: () -> T,
  checker: (T) -> Boolean,
): T {
  logAwaitStart(message, timeout)
  val start = TimeSource.Monotonic.markNow()
  while (true) {
    val result = getter()
    if (checker(result)) {
      val passedTime = start.elapsedNow()
      if (result !is Boolean || passedTime > 10.seconds) {
        logAwaitFinish(message, result, passedTime)
      }
      return result
    }
    if (start.elapsedNow() >= timeout) {
      throw WaitForException(timeout,
                             errorMessage = errorMessage?.invoke(result)
                                            ?: failure(null, message, if (result !is Boolean) "Actual: $result" else null))
        .also { LOG.warn(it) }
    }
    Thread.sleep(interval.inWholeMilliseconds)
  }
}

/** See docs for another [waitFor] */
suspend fun <T> waitForAsync(
  message: String? = null,
  timeout: Duration = 5.seconds,
  interval: Duration = 1.seconds,
  errorMessage: ((T) -> String)? = null,
  getter: suspend () -> T,
  checker: suspend (T) -> Boolean,
): T {
  var result: T? = null
  var observed = false
  return try {
    withTimeout(timeout) {
      val startTime = System.nanoTime()
      while (true) {
        result = getter()
        observed = true
        if (checker(result)) {
          break
        }
        delay(interval)
      }
      val passedTime = (System.nanoTime() - startTime).nanoseconds
      if (result !is Boolean || passedTime > 10.seconds) {
        logAwaitFinish(message, result, passedTime)
      }
      result
    }
  }
  catch (_: TimeoutCancellationException) {
    currentCoroutineContext().ensureActive()
    @Suppress("UNCHECKED_CAST")
    val errorMessage =
      if (errorMessage != null && observed) errorMessage.invoke(result as T)
      else failure(null, message, when {
        !observed -> "The getter returned no value"
        result is Boolean -> null
        else -> "Actual: $result"
      })
    val exception = WaitForException(timeout, errorMessage)
    LOG.warn(exception)
    throw exception
  }
}

/**
 * Waits for exactly one suitable element in a list and returns it.
 *
 * @return the suitable element from the list
 * @throws WaitForException if no suitable element is found within the specified timeout
 */
fun <T> waitForOne(
  message: String? = null,
  timeout: Duration = 5.seconds,
  interval: Duration = 1.seconds,
  errorMessage: ((Collection<T>) -> String)? = null,
  getter: () -> Collection<T>,
  checker: (T) -> Boolean = { true },
): T {
  return waitFor(message = message, timeout = timeout,
                 interval = interval,
                 errorMessage = { (all, suitable, notSuitable) ->
                   errorMessage?.invoke(all)
                   ?: (failure("Await one", message, null) + section("suitable", suitable) + section("not suitable", notSuitable))
                 },
                 getter = { observe(getter(), checker) },
                 checker = { it.suitable.size == 1 }
  ).suitable.single()
}

/**
 * Calls [f] up to [times] times until it completes without an exception.
 *
 * A retry can hide a real problem. Find the cause of the failure first, and wait for the correct UI state instead.
 * Do not add a retry to a test-sdk helper. If a test really needs a retry, keep it in the test module.
 *
 * @throws RetryException if all attempts fail
 */
fun <T> withRetries(message: String? = null, times: Int, onError: () -> Unit = {}, f: () -> T): T {
  message?.let { LOG.info("With $times attempts: $it") }
  require(times > 0)
  var lastException: Exception? = null
  for (i in 0 until times) {
    try {
      return f()
    }
    catch (e: Exception) {
      onError()
      lastException = e
    }
  }
  throw RetryException(lastException!!).also { LOG.warn(it) }
}

class WaitForException(val timeout: Duration, val errorMessage: String, cause: Throwable? = null)
  : IllegalStateException(if (errorMessage.isEmpty()) "Timeout($timeout)" else "Timeout($timeout): $errorMessage", cause)
class RetryException(cause: Exception) : RuntimeException(cause)

fun <T : UiComponent> T.wait(timeout: Duration): T {
  Thread.sleep(timeout.inWholeMilliseconds)
  return this
}