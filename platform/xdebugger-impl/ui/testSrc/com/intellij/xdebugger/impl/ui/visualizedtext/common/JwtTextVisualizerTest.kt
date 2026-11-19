// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class JwtTextVisualizerTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val checker = TextVisualizerChecker(JwtTextVisualizer(), projectFixture)

  @Test
  fun testSomeValidJwt(): Unit = timeoutRunBlocking {
    checker.checkPositive(
      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
        "eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ." +
        "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c",
      """
        {
          "header" : {
            "alg" : "HS256",
            "typ" : "JWT"
          },
          "payload" : {
            "sub" : "1234567890",
            "name" : "John Doe",
            "iat" : 1516239022
          },
          "signature" : "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        }
      """.trimIndent())
  }

  @Test
  fun testNotJwt() {
    checker.checkNegative("Hello.world.!")
  }
}