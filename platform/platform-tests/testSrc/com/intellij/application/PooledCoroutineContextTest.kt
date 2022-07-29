// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.getValue
import com.intellij.util.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.*
import java.util.concurrent.atomic.AtomicReference

@RunWith(JUnit4::class)
class PooledCoroutineContextTest : UsefulTestCase() {

  @Test
  fun `log error`() {
    val exception = RuntimeException()
    assertTrue(exception === loggedErrorsAfterThrowingFromGlobalScope(exception))
  }

  @Test
  fun `do not log ProcessCanceledException`() {
    val exception = ProcessCanceledException()
    assertNull(loggedErrorsAfterThrowingFromGlobalScope(exception))
  }

  private fun loggedErrorsAfterThrowingFromGlobalScope(exception: Throwable): Throwable? = withNoopThreadUncaughtExceptionHandler {
    loggedError {
      runBlocking {
        @Suppress("EXPERIMENTAL_API_USAGE")
        GlobalScope.launch(Dispatchers.IO) {
          throw exception
        }.join()
      }
    }
  }

  private fun loggedError(block: () -> Unit): Throwable? {
    var throwable by AtomicReference<Throwable?>()
    LoggedErrorProcessor.executeWith<RuntimeException>(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): MutableSet<Action> {
        throwable = t
        return Action.NONE
      }
    }, block)
    return throwable
  }

  private fun <T> withNoopThreadUncaughtExceptionHandler(block: () -> T): T {
    val savedHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
    try {
      return block()
    }
    finally {
      Thread.setDefaultUncaughtExceptionHandler(savedHandler)
    }
  }
}
