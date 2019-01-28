// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application

import com.intellij.testFramework.assertions.Assertions.assertThat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Test

class PooledCoroutineContextTest {
  @Test
  fun `log error`() = runBlocking<Unit> {
    val errorMessage = "don't swallow me"
    // cannot use assertThatThrownBy here, because AssertJ doesn't support Kotlin coroutines
    try {
      GlobalScope.launch(pooledThreadContext) {
        throw RuntimeException(errorMessage)
      }.join()
    }
    catch (e: AssertionError) {
      // in tests logger throws AssertionError on log.error()
      assertThat(e).hasMessage(errorMessage)
    }
  }

  @Test
  fun `error must be propagated to parent context if available`() = runBlocking {
    class MyCustomException : RuntimeException()

    try {
      withContext(pooledThreadContext) {
        throw MyCustomException()
      }
    }
    catch (ignored: MyCustomException) {
    }
  }
}