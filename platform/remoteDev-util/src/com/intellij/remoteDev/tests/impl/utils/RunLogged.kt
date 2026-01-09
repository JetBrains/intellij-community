package com.intellij.remoteDev.tests.impl.utils

import com.intellij.concurrency.currentThreadContext
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.remoteDev.tests.impl.RdctTestFrameworkLoggerCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.measureTimedValue

// it is easier to sort out logs from just testFramework
internal val LOG = Logger.getInstance(RdctTestFrameworkLoggerCategory.category).also {
  it.setLevel(LogLevel.INFO)
}

@TestOnly
@ApiStatus.Internal
suspend fun <T> runLogged(actionTitle: String, timeout: Duration? = null, action: suspend () -> T): T {
  val (result, passedTime) = measureTimedValue {
    try {
      if (timeout != null) {
        LOG.info("'$actionTitle': starting with $timeout timeout on ${currentThreadContext()}")
        withTimeoutDumping(actionTitle, timeout) {
          action()
        }
      }
      else {
        LOG.info("'$actionTitle': starting on ${currentThreadContext()}")
        action()
      }
    }
    catch (e: CancellationException) {
      LOG.warn("'$actionTitle': was CANCELLED on ${currentThreadContext()}")
      throw e
    }
    catch (e: Throwable) {
      LOG.warn("'$actionTitle': failed", e)
      throw e
    }
  }
  val resultString = if (result != null && result !is Unit) " with result '$result'" else ""
  LOG.info("'$actionTitle': finished in ${passedTime.customToString}$resultString")
  return result
}

@Suppress("SSBasedInspection")
@TestOnly
@ApiStatus.Internal
fun <T> runLoggedBlocking(actionTitle: String, action: () -> T): T =
  runBlocking {
    runLogged(actionTitle) {
        action.invoke()
      }
  }

private val Duration.customToString: String
  get() = toString(unit = DurationUnit.SECONDS, decimals = 3)