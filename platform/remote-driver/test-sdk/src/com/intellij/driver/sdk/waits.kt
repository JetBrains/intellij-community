package com.intellij.driver.sdk

import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.printableString
import com.intellij.openapi.diagnostic.fileLogger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val LOG get() = fileLogger()

/**
 * Waits for a condition to be met within a specified timeout period.
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
                 errorMessage = if (errorMessage == null) {
                   null
                 }
                 else { it -> errorMessage.invoke(it) },
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
                 errorMessage = if (errorMessage == null) {
                   null
                 }
                 else { it -> errorMessage.invoke(it) },
                 getter = getter,
                 checker = { it.any { checker(it) } }
  ).filter { checker(it) }
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
  val startTime = System.currentTimeMillis()
  val endTime = startTime + timeout.inWholeMilliseconds
  var result = getter()
  while (endTime > System.currentTimeMillis() && checker(result).not()) {
    Thread.sleep(interval.inWholeMilliseconds)
    result = getter()
  }
  if (checker(result).not()) {
    throw WaitForException(timeout,
                           errorMessage = errorMessage?.invoke(result)
                                          ?: ("Failed: $message" + if (result !is Boolean) ". Actual: $result" else ""))
      .also { LOG.warn(it) }
  }
  else {
    val passedTime = (System.currentTimeMillis() - startTime).milliseconds
    if (result !is Boolean || passedTime > 10.seconds) {
      logAwaitFinish(message, result, passedTime)
    }
    return result
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
  errorMessage: ((List<T>) -> String)? = null,
  getter: () -> List<T>,
  checker: (T) -> Boolean = { true },
): T {
  logAwaitStart(message, timeout)
  var resultList = getter()
  var filteredResultList = resultList.filter { checker(it) }
  val startTime = System.currentTimeMillis()
  val endTime = startTime + timeout.inWholeMilliseconds
  while (endTime > System.currentTimeMillis() && filteredResultList.size != 1) {
    Thread.sleep(interval.inWholeMilliseconds)
    resultList = getter()
    filteredResultList = resultList.filter { checker(it) }
  }
  if (filteredResultList.size != 1) {
    val resultListString = if (resultList.isEmpty()) "none" else resultList.joinToString("\n\t")
    throw WaitForException(timeout,
                           errorMessage = errorMessage?.invoke(resultList)
                                          ?: ("Failed: $message. " +
                                              "\n\tExpected one suitable instance, but got:" +
                                              "\n\t$resultListString"))
      .also { LOG.warn(it) }
  }
  else {
    return filteredResultList.single().also {
      val passedTime = (System.currentTimeMillis() - startTime).milliseconds
      logAwaitFinish(message, it, passedTime)
    }
  }
}

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

class WaitForException(val timeout: Duration, val errorMessage: String, cause: Throwable? = null) : IllegalStateException("Timeout($timeout): $errorMessage", cause)
class RetryException(cause: Exception) : RuntimeException(cause)

fun <T : UiComponent> T.wait(timeout: Duration): T {
  Thread.sleep(timeout.inWholeMilliseconds)
  return this
}