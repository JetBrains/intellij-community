// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.assertions.Assertions.assertThat
import kotlinx.coroutines.*
import org.apache.log4j.Logger
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.*

@RunWith(JUnit4::class)
class PooledCoroutineContextTest : UsefulTestCase() {
  @Test
  fun `log error`() = runBlocking<Unit> {
    val errorMessage = "don't swallow me"
    val loggedErrors = loggedErrorsAfterThrowingFromGlobalScope(RuntimeException(errorMessage))

    assertThat(loggedErrors).anyMatch { errorMessage in it.message.orEmpty() }
  }

  @Test
  fun `do not log ProcessCanceledException`() = runBlocking<Unit> {
    val errorMessage = "ignore me"
    val loggedErrors = loggedErrorsAfterThrowingFromGlobalScope(ProcessCanceledException(RuntimeException(errorMessage)))

    assertThat(loggedErrors).noneMatch { errorMessage in it.cause?.message.orEmpty() }
  }

  private suspend fun loggedErrorsAfterThrowingFromGlobalScope(exception: Throwable): List<Throwable> {
    // cannot use assertThatThrownBy here, because AssertJ doesn't support Kotlin coroutines
    val loggedErrors = mutableListOf<Throwable>()
    withLoggedErrorsRecorded(loggedErrors) {
      GlobalScope.launch(Dispatchers.ApplicationThreadPool) {
        throw exception
      }.join()
    }

    return loggedErrors
  }

  private suspend fun <T> withLoggedErrorsRecorded(loggedErrors: List<Throwable>,
                                                   block: suspend () -> T): T {
    val savedInstance = LoggedErrorProcessor.getInstance()
    val synchronizedLoggedErrors = Collections.synchronizedList(loggedErrors)
    LoggedErrorProcessor.setNewInstance(object : LoggedErrorProcessor() {
      override fun processError(message: String, t: Throwable, details: Array<String>, logger: Logger) {
        synchronizedLoggedErrors.add(t)
      }
    })
    return try {
      withNoopThreadUncaughtExceptionHandler { block() }
    }
    finally {
      LoggedErrorProcessor.setNewInstance(savedInstance)
    }
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