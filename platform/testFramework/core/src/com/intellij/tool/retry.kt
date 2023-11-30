// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tool

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** @return T - if successful; null - otherwise */
suspend fun <T> withRetryAsync(retries: Int = 3,
                               messageOnFailure: String = "",
                               delayBetweenRetries: Duration = 10.seconds,
                               retryAction: suspend () -> T): T? {

  (1..retries).forEach { failureCount ->
    try {
      return retryAction()
    }
    catch (t: Throwable) {
      // print failure details only once for the sake of log cleanliness
      if (failureCount == 1) {
        if (messageOnFailure.isNotBlank()) {
          System.err.println(messageOnFailure)
        }
        t.printStackTrace()
      }

      if (failureCount < retries) {
        System.err.println("Retrying in $delayBetweenRetries ...")
        delay(delayBetweenRetries)
      }
    }
  }

  return null
}


/** @return T - if successful; null - otherwise */
fun <T> withRetry(retries: Int = 3,
                  messageOnFailure: String = "",
                  delayBetweenRetries: Duration = 5.seconds,
                  retryAction: () -> T): T? = runBlocking {
  withRetryAsync(retries = retries, messageOnFailure = messageOnFailure, delayBetweenRetries = delayBetweenRetries) { retryAction() }
}
