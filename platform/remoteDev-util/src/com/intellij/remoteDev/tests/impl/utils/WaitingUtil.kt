package com.intellij.remoteDev.tests.impl.utils

import com.intellij.internal.DebugAttachDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

private fun <T> defaultFailMessageProvider(result: T) = "Actual: '${result.toString()}'"


// Determines if long timeout is used for the local debug of the tests.
private const val defaultLongWaitingOnDebug = true
private val debugLongTimeout = 30.minutes
internal val isDebugging by lazy { DebugAttachDetector.isAttached() }

fun getTimeoutHonouringDebug(providedTimeout: Duration): Duration =
  if (isDebugging && defaultLongWaitingOnDebug) {
    debugLongTimeout
  }
  else {
    providedTimeout
  }

fun getTimeoutHonouringDebug(providedTimeout: Duration?, defaultTimeout: Duration): Duration =
  getTimeoutHonouringDebug(providedTimeout ?: defaultTimeout)


suspend fun <T : Any> waitSuspendingNotNull(
  subjectOfWaiting: String,
  timeout: Duration,
  delay: Duration = 300.milliseconds,
  dynamicallyIncreaseDelay: Boolean = false,
  failMessageProducer: ((T?) -> String) = { "" },
  getter: suspend () -> T?,
): T =
  waitSuspending(subjectOfWaiting, timeout, delay, dynamicallyIncreaseDelay, failMessageProducer, getter) { it != null }!!

suspend fun waitSuspending(
  subjectOfWaiting: String,
  timeout: Duration,
  delay: Duration = 300.milliseconds,
  dynamicallyIncreaseDelay: Boolean = false,
  failMessageProducer: ((Boolean?) -> String) = { defaultFailMessageProvider(it) },
  checker: suspend () -> Boolean,
) {
  waitSuspending(subjectOfWaiting, timeout, delay, dynamicallyIncreaseDelay, failMessageProducer, checker) { it }
}

/**
 * Suspends the execution until a certain condition is met.
 *
 * @param subjectOfWaiting A string describing what we are waiting for.
 * @param timeout The maximum duration to wait.
 * @param failMessageProducer A function that produces the fail message if the waiting condition is not met.
 * @param getter A suspend function that retrieves the value being checked.
 * @param checker A suspend function that checks whether the value meets the desired condition.
 * @return The final value that satisfies the waiting condition.
 *
 * @throws TimeoutCancellationException if the waiting exceeds the specified timeout.
 */
suspend fun <T> waitSuspending(
  subjectOfWaiting: String,
  timeout: Duration,
  delay: Duration = 300.milliseconds,
  dynamicallyIncreaseDelay: Boolean = false,
  failMessageProducer: ((T?) -> String) = { defaultFailMessageProvider(it) },
  getter: suspend () -> T,
  checker: suspend (T) -> Boolean,
): T {
  val timeoutHonouringDebug = getTimeoutHonouringDebug(timeout)
  return runLogged("$subjectOfWaiting with $timeoutHonouringDebug timeout${if (isDebugging) "[debug]" else ""}") {
    var result: T = getter()

    var toDelay = delay
    withTimeoutDumping(
      title = subjectOfWaiting,
      timeout = timeoutHonouringDebug,
      action = {
        while (!checker(result)) {
          delay(toDelay)
          result = getter()
          if (dynamicallyIncreaseDelay) {
            toDelay *= 2
          }
        }
        result
      },
      failMessageProducer = { failMessageProducer(result) + "\n" + defaultFailMessageProvider(result) }
    )
  }
}

suspend fun <T> waitSuspendingForOne(
  subjectOfWaiting: String,
  timeout: Duration,
  delay: Duration = 300.milliseconds,
  dynamicallyIncreaseDelay: Boolean = false,
  failMessageProducer: ((Collection<T>) -> String) = { defaultFailMessageProvider(it) },
  getter: suspend () -> Collection<T>,
  checker: suspend (T) -> Boolean = { true },
): T =
  runLogged("$subjectOfWaiting with $timeout timeout") {
    var resultList = getter()
    var suitableOne = getter().singleOrNull { checker.invoke(it) }

    var toDelay = delay
    withTimeoutDumping(
      title = subjectOfWaiting,
      timeout = getTimeoutHonouringDebug(timeout),
      action = {
        while (suitableOne == null) {
          delay(toDelay)
          resultList = getter()
          suitableOne = resultList.singleOrNull { checker.invoke(it) }
          if (dynamicallyIncreaseDelay) {
            toDelay *= 2
          }
        }
        suitableOne!!
      },
      failMessageProducer = { failMessageProducer(resultList) + "\n" + defaultFailMessageProvider(resultList) }
    )
  }


suspend fun <T> waitSuspendingAssertIsTrue(
  subjectOfWaiting: String,
  timeout: Duration,
  delay: Duration = 300.milliseconds,
  dynamicallyIncreaseDelay: Boolean = false,
  getter: suspend () -> T,
  assertingCode: (T) -> Unit,
): T =
  runLogged("$subjectOfWaiting with $timeout timeout") {
    var result: Result<T> = runCatching { getter().also(assertingCode) }

    var toDelay = delay
    withTimeoutDumping(
      title = subjectOfWaiting,
      timeout = getTimeoutHonouringDebug(timeout),
      action = {
        while (!result.isSuccess) {
          delay(toDelay)
          result = runCatching { getter().also(assertingCode) }
          if (dynamicallyIncreaseDelay) {
            toDelay *= 2
          }
        }
        result
      },
      failMessageProducer = { result.toString() }
    )
    return@runLogged result.getOrThrow()
  }

suspend fun waitSuspendingAssertIsTrue(
  subjectOfWaiting: String,
  timeout: Duration,
  delay: Duration = 300.milliseconds,
  dynamicallyIncreaseDelay: Boolean = false,
  assertingCode: () -> Unit,
): Unit =
  waitSuspendingAssertIsTrue(subjectOfWaiting, timeout, delay, dynamicallyIncreaseDelay, { }) {
    assertingCode.invoke()
  }

fun <T> waitAssertIsTrue(
  subjectOfWaiting: String,
  timeout: Duration,
  delay: Duration = 300.milliseconds,
  dynamicallyIncreaseDelay: Boolean = false,
  getter: suspend () -> T,
  assertingCode: (T) -> Unit,
): T =
  runBlocking {
    waitSuspendingAssertIsTrue(subjectOfWaiting, timeout, delay, dynamicallyIncreaseDelay, getter, assertingCode)
  }