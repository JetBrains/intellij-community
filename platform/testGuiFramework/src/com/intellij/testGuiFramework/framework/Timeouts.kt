// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.framework

import org.fest.swing.timing.Pause
import org.fest.swing.timing.Timeout
import java.util.concurrent.TimeUnit

object Timeouts {
  val defaultTimeout = Timeout.timeout(2, TimeUnit.MINUTES)
  val noTimeout = Timeout.timeout(0, TimeUnit.SECONDS)

  val seconds01 = Timeout.timeout(1, TimeUnit.SECONDS)
  val seconds02 = Timeout.timeout(2, TimeUnit.SECONDS)
  val seconds03 = Timeout.timeout(3, TimeUnit.SECONDS)
  val seconds05 = Timeout.timeout(5, TimeUnit.SECONDS)
  val seconds10 = Timeout.timeout(10, TimeUnit.SECONDS)
  val seconds30 = Timeout.timeout(30, TimeUnit.SECONDS)
  val minutes01 = Timeout.timeout(1, TimeUnit.MINUTES)
  val minutes02 = Timeout.timeout(2, TimeUnit.MINUTES)
  val minutes05 = Timeout.timeout(5, TimeUnit.MINUTES)
  val minutes10 = Timeout.timeout(10, TimeUnit.MINUTES)
  val minutes15 = Timeout.timeout(15, TimeUnit.MINUTES)
  val minutes20 = Timeout.timeout(20, TimeUnit.MINUTES)
  val hours01 = Timeout.timeout(1, TimeUnit.HOURS)

}

fun Timeout.wait() {
  Pause.pause(this.duration())
}

fun Timeout.toPrintable(): String {
  return when {
    this.duration() >= 60000 -> "${this.duration() / 60000}(m)"
    this.duration() >= 1000 -> "${this.duration() / 1000}(s)"
    else -> "${this.duration()}(ms)"
  }
}
