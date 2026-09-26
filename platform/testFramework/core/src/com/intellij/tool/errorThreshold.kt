// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tool

import java.util.concurrent.ConcurrentHashMap

/** Object name to Errors limit number */
private val errorsCount = ConcurrentHashMap<String, Int>()


/**
 * Prevents any action from executing if the failure threshold was hit.
 * Doesn't guarantee precise count of events.
 * @return T - if action completed successfully without violation of error threshold, null - Error threshold has been reached
 * @throws Retrow original Throwable
 **/
fun <T> withErrorThreshold(objName: String, errorThreshold: Int = 3, action: () -> T, fallbackOnThresholdReached: () -> T): T {
  val errorNumber: Int? = errorsCount[objName]

  if (errorNumber != null) {
    // print error details only once to not pollute log
    if (errorNumber == errorThreshold) {
      System.err.println("Error threshold for `$objName` is exceeded $errorThreshold. Skipping any further actions with it.")
    }

    if (errorNumber >= errorThreshold) return fallbackOnThresholdReached()
  }

  try {
    return action()
  }
  catch (e: Throwable) {
    errorsCount.computeIfPresent(objName) { key, value -> value + 1 }

    if (errorsCount.putIfAbsent(objName, 1) == null) {
      // print error message only on first failure
      System.err.println("First failure for $objName. Next failures will not be printed.")
      e.printStackTrace()
    }

    throw e
  }
}