// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.coroutines

import com.intellij.platform.util.coroutines.flow.throttle
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

class ThrottleTest {
  @Test
  fun `the documented example emits 1, 3 and 4 and completes with the upstream`() = runTest {
    val emissions = flow {
      delay(40.milliseconds)
      emit(1)
      delay(40.milliseconds)
      emit(2)
      delay(40.milliseconds)
      emit(3)
      delay(40.milliseconds)
      emit(4)
    }.throttle(100).toList()

    emissions shouldContainExactly listOf(1, 3, 4)
  }
}
