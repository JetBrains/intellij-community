// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.fail
import org.opentest4j.MultipleFailuresError

@TestOnly
internal class TestUncaughtExceptionHandler : Thread.UncaughtExceptionHandler {

  private val uncaughtExceptions = ContainerUtil.createConcurrentList<Throwable>()

  override fun uncaughtException(t: Thread, e: Throwable) {
    uncaughtExceptions.add(e)
  }

  fun assertAllExceptionAreCaught() {
    val e = when (uncaughtExceptions.size) {
      0 -> return
      1 -> uncaughtExceptions[0]
      else -> MultipleFailuresError(null, uncaughtExceptions)
    }
    fail("Uncaught exceptions", e)
  }
}
