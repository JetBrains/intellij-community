// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class NoProjectLeakOnCancellation {
  val tempDir by tempPathFixture()

  @Test
  fun noLeaksReported(): Unit = timeoutRunBlocking {
      val project = openProjectAsync(tempDir)

      withTimeoutOrNull(1.seconds) {
          try {
              delay(10.seconds)
          } finally {
              project.closeProjectAsync()
          }
      }
  }
}
