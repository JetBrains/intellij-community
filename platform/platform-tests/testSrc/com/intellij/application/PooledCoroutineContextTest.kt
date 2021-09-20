// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.assertions.Assertions.assertThat
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.*
import java.util.concurrent.atomic.AtomicReference

@RunWith(JUnit4::class)
class PooledCoroutineContextTest : UsefulTestCase() {
  @Test
  fun `log error`() {
    val errorMessage = "don't swallow me"
    val loggedErrors = loggedErrorsAfterThrowingFromGlobalScope(RuntimeException(errorMessage))

    assertThat(loggedErrors).anyMatch { errorMessage in it.message.orEmpty() }
  }

  @Test
  fun `do not log ProcessCanceledException`() {
    val errorMessage = "ignore me"
    val loggedErrors = loggedErrorsAfterThrowingFromGlobalScope(ProcessCanceledException(RuntimeException(errorMessage)))

    assertThat(loggedErrors).noneMatch { errorMessage in it.cause?.message.orEmpty() }
  }

  private fun loggedErrorsAfterThrowingFromGlobalScope(exception: Throwable): List<Throwable> {
    // cannot use assertThatThrownBy here, because AssertJ doesn't support Kotlin coroutines
    val loggedErrors = mutableListOf<Throwable>()
    withLoggedErrorsRecorded(loggedErrors) {
      runBlocking<Unit> {
        withNoopThreadUncaughtExceptionHandler {
          GlobalScope.launch(Dispatchers.ApplicationThreadPool) {
            throw exception
          }.join()
        }
      }
    }
    return loggedErrors
  }

  private fun <T> withLoggedErrorsRecorded(loggedErrors: List<Throwable>,
                                                   block: () -> T): T {
    val synchronizedLoggedErrors = Collections.synchronizedList(loggedErrors)
    val result:AtomicReference<T> = AtomicReference()
    LoggedErrorProcessor.executeWith<RuntimeException>(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String?, t: Throwable?, details: Array<out String>): Boolean {
        synchronizedLoggedErrors.add(t)
        return false
      }
    }) {
      result.set(block())
    }
    return result.get()
  }

  private suspend fun <T> withNoopThreadUncaughtExceptionHandler(block: suspend () -> T): T {
    val savedHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { _, _ -> }
    return try {
      block()
    }
    finally {
      Thread.setDefaultUncaughtExceptionHandler(savedHandler)
    }
  }

  @Test
  fun `error must be propagated to parent context if available`() = runBlocking {
    class MyCustomException : RuntimeException()

    try {
      withContext(Dispatchers.ApplicationThreadPool) {
        throw MyCustomException()
      }
    }
    catch (ignored: MyCustomException) {
    }
  }
}
