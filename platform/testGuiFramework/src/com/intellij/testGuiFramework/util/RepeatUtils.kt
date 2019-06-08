// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util

import org.fest.swing.exception.WaitTimedOutError

/*
  Wait for @condition during @timeOutSeconds
 */
inline fun waitFor(timeOutSeconds: Int = 5, intervalMillis: Long = 500, crossinline condition: () -> Boolean) {
  val endTime = System.currentTimeMillis() + timeOutSeconds * 1000
  while (System.currentTimeMillis() < endTime) {
    if (condition()) {
      return
    }
    else {
      Thread.sleep(intervalMillis)
    }
  }
  throw WaitTimedOutError("Failed to wait for condition in $timeOutSeconds seconds")
}

fun attempt(times: Int, func: () -> Unit) {
  var exception: Exception? = null
  for (i in 0 until times) {
    try {
      return func()
    } catch (e: Exception) {
      if (exception == null) {
        exception = e
      } else {
        exception.addSuppressed(e)
      }
    }
  }
  throw exception!!
}