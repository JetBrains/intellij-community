// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tool

import java.util.concurrent.ConcurrentHashMap

/** Object name to Error number */
private val errorLimits = ConcurrentHashMap<String, Int>()


/**
 * Prevents any action from executing if the failure threshold was hit.
 * Doesn't guarantee precise count of events.
 * @throws RuntimeException in case if threshold has been reached.
 **/
fun <T> withErrorThreshold(objName: String, errorThreshold: Int = 3, action: () -> T): T {
  val currentLimit: Int? = errorLimits[objName]

  if (currentLimit != null && currentLimit >= errorThreshold) {
    throw RuntimeException("Error threshold for `$objName` is exceeded $errorThreshold. Skipping any further actions")
  }

  try {
    return action()
  }
  catch (e: Throwable) {
    errorLimits.computeIfPresent(objName) { key, value -> value + 1 }
    errorLimits.putIfAbsent(objName, 1)

    throw e
  }
}