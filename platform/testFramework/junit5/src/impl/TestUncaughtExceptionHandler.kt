// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.fail
import java.io.ByteArrayOutputStream
import java.io.PrintStream

@TestOnly
internal class TestUncaughtExceptionHandler : Thread.UncaughtExceptionHandler {

  private val uncaughtExceptions = ContainerUtil.createConcurrentList<Throwable>()

  override fun uncaughtException(t: Thread, e: Throwable) {
    uncaughtExceptions.add(e)
  }

  fun assertAllExceptionAreCaught() {
    if (uncaughtExceptions.isEmpty()) return

    val bStream = ByteArrayOutputStream()

    PrintStream(bStream).use { stream ->
      uncaughtExceptions.forEachIndexed { index, throwable ->
        stream.println("${index + 1}) ")
        throwable.printStackTrace(stream)
      }

      fail("${uncaughtExceptions.size} uncaught exceptions:${System.lineSeparator()}" +
           bStream.toString())
    }
  }
}
