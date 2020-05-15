// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.filePrediction

import java.util.concurrent.atomic.AtomicInteger

private val counter = AtomicInteger(0)

internal class FilePredictionSessionHolder {
  private var session: FilePredictionSession? = null

  @Synchronized
  fun getSession(): FilePredictionSession? = session

  @Synchronized
  fun newSession(): FilePredictionSession? {
    session = FilePredictionSession()
    return session
  }
}

internal class FilePredictionSession {
  val id = counter.incrementAndGet()
  private val loggingProbability = Math.random()

  fun shouldLog(threshold: Double): Boolean {
    return loggingProbability < threshold
  }
}