// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.testFramework.assertNothingLogged
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ValueSource

@ParameterizedClass
@ValueSource(booleans = [true, false])
internal class AssertWarnIsErrorShowCaseTest(private val failOnWarn: Boolean) {
  private companion object {
    val logger = fileLogger()
  }

  @Test
  fun testSuccess() {
    assertNothingLogged(failOnWarn = failOnWarn) {

    }
  }

  @Test
  fun testWarn() {
    fun makeWarn() {
      assertNothingLogged(failOnWarn = failOnWarn) {
        logger.warn("aaa")
      }
    }
    if (failOnWarn) {
      assertThrows<AssertionError> {
        makeWarn()
      }
    }
    else {
      makeWarn()
    }
  }

  @Test
  fun testError() {
    assertThrows<AssertionError> {
      assertNothingLogged {
        logger.error("dd")
      }
    }
  }
}
