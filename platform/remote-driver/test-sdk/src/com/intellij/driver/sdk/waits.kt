package com.intellij.driver.sdk

import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.printableString
import com.intellij.openapi.diagnostic.fileLogger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val LOG get() = fileLogger()

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

private fun logAwaitStart(message: String?, timeout: Duration) {
  message?.let { LOG.info("Await: '$it' with timeout $timeout") }
}

private fun <T> logAwaitFinish(message: String?, result: T) {
  message?.let {
    LOG.info("Await: '$it' resulted with \n\t${printableString(result.toString())}")
  }
}


fun <T> waitFor(
  message: String? = null,
  timeout: Duration = 5.seconds,
  interval: Duration = 1.seconds,
  errorMessage: ((T) -> String)? = null,
  getter: () -> T,
  checker: (T) -> Boolean,
): T {
  logAwaitStart(message, timeout)
  val endTime = System.currentTimeMillis() + timeout.inWholeMilliseconds
  var now = System.currentTimeMillis()
  var result = getter()
  while (now < endTime && checker(result).not()) {
    Thread.sleep(interval.inWholeMilliseconds)
    result = getter()
    now = System.currentTimeMillis()
  }
  if (checker(result).not()) {
    throw WaitForException(timeout,
                           errorMessage = errorMessage?.invoke(result)
                                          ?: ("Failed: $message" + if (result !is Boolean) ". Actual: $result" else ""))
  }
  else {
    if (result !is Boolean) {
      logAwaitFinish(message, result)
    }
    return result
  }
}

/**
 * Waits until there is exactly one match in getter result abiding checker.
 */
fun <T> waitForOne(
  message: String? = null,
  timeout: Duration = 5.seconds,
  interval: Duration = 1.seconds,
  errorMessage: ((List<T>) -> String)? = null,
  getter: () -> List<T>,
  checker: (T) -> Boolean,
): T {
  logAwaitStart(message, timeout)
  val endTime = System.currentTimeMillis() + timeout.inWholeMilliseconds
  var now = System.currentTimeMillis()
  var resultList = getter()
  var filteredResultList = resultList.filter { checker(it) }
  while (now < endTime && filteredResultList.size != 1) {
    Thread.sleep(interval.inWholeMilliseconds)
    resultList = getter()
    filteredResultList = resultList.filter { checker(it) }
    now = System.currentTimeMillis()
  }
  if (filteredResultList.size != 1) {
    throw WaitForException(timeout,
                           errorMessage = errorMessage?.invoke(resultList)
                                          ?: ("Failed: $message. " +
                                              "\n\tExpected one suitable instance, but got: " +
                                              "\n\tReceived list: ${resultList.joinToString("\n\t")}" +
                                              "\n\tSuitable list: ${filteredResultList.joinToString("\n\t")}"))
  }
  else {
    return filteredResultList.single().also {
      logAwaitFinish(message, it)
    }
  }
}

fun <T> waitForOne(
  message: String? = null,
  timeout: Duration = 5.seconds,
  interval: Duration = 1.seconds,
  errorMessage: ((List<T>) -> String)? = null,
  getter: () -> List<T>,
): T {
  logAwaitStart(message, timeout)
  val endTime = System.currentTimeMillis() + timeout.inWholeMilliseconds
  var now = System.currentTimeMillis()
  var resultList = getter()
  while (now < endTime && resultList.size != 1) {
    Thread.sleep(interval.inWholeMilliseconds)
    resultList = getter()
    now = System.currentTimeMillis()
  }
  if (resultList.size != 1) {
    throw WaitForException(timeout,
                           errorMessage = errorMessage?.invoke(resultList)
                                          ?: ("Failed: $message. " +
                                              "\n\tExpected one suitable instance, but got:" +
                                              "\n\t${resultList.joinToString("\n\t")}"))
  }
  else {
    return resultList.single().also {
      logAwaitFinish(message, it)
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
  throw RetryException(lastException!!)
}

class WaitForException(val timeout: Duration, val errorMessage: String, cause: Throwable? = null) : IllegalStateException("Timeout($timeout): $errorMessage", cause)
class RetryException(cause: Exception) : RuntimeException(cause)

fun <T : UiComponent> T.wait(timeout: Duration): T {
  Thread.sleep(timeout.inWholeMilliseconds)
  return this
}