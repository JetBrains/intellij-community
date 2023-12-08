package com.intellij.remoteDev.tests.impl

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

// it is easier to sort out logs from just testFramework
private val LOG = Logger.getInstance(RdctTestFrameworkLoggerCategory.category)

@TestOnly
@ApiStatus.Internal
suspend fun <T> runLogged(actionTitle: String, timeout: Duration? = null, action: suspend () -> T): T {
  val (result, passedTime) = measureTimedValue {
    if (timeout != null) {
      LOG.info("'$actionTitle': starting with $timeout timeout on ${Thread.currentThread()}")
      withTimeoutDumping(actionTitle, timeout) {
        action()
      }
    }
    else {
      LOG.info("'$actionTitle': starting on ${Thread.currentThread()}")
      action()
    }
  }
  val resultString = if (result != null && result !is Unit) " with result '$result'" else ""
  LOG.info("'$actionTitle': finished in ${passedTime.customToString}$resultString")
  return result
}

fun <T> runLoggedBlocking(actionTitle: String, action: () -> T): T {
  LOG.info("'$actionTitle': starting on ${Thread.currentThread()}")
  val (result, passedTime) = measureTimedValue {
    action()
  }

  val resultString = if (result != null && result !is Unit) " with result '$result'" else ""
  LOG.info("'$actionTitle': finished in ${passedTime.customToString}$resultString")
  return result
}

val Duration.customToString: String
  get() = toString(unit = DurationUnit.SECONDS, decimals = 3)