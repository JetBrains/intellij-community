// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util

/*
  Wait for @condition during @seconds
 */
inline fun waitFor(seconds: Int = 5, crossinline condition: () -> Boolean) {
  val endTime = System.currentTimeMillis() + seconds * 1000
  while (System.currentTimeMillis() < endTime) {
    if (condition()) {
      return
    }
    else {
      Thread.sleep(500)
    }
  }
  throw IllegalStateException("Failed to wait for condition in $seconds seconds")
}