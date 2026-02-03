// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.concurrency

import com.intellij.platform.util.coroutines.transformConcurrent
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.delay
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

class TransformConcurrentCancellationPropagationTest {
  @ParameterizedTest(name = "Concurrency: {1}")
  @MethodSource("intsListAndConcurrency")
  fun transformConcurrent(intsList: List<Int>, concurrency: Int) {
    suspend fun foo(i: Int): Int {
      delay(i.seconds)
      if (i > intsList.size / 2) throw CancellationException("foo cancelled")
      return i
    }

    timeoutRunBlocking {
      assertThrows<CancellationException> {
        intsList.transformConcurrent<Int, Int>(concurrency) {
          foo(it)
        }
      }
    }
  }

  companion object {
    private val intsList = (0..3).toList()

    @JvmStatic
    private fun intsListAndConcurrency() =
      Stream.of(
        Arguments.of(intsList, 1),
        Arguments.of(intsList, intsList.size / 2),
        Arguments.of(intsList, intsList.size + 1)
      )
  }
}
