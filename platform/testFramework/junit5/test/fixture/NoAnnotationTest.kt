// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class NoAnnotationTest {
  @Test
  fun testNoAnnotation() {
    Assertions.assertThrows<IllegalStateException>(IllegalStateException::class.java) {
      projectFixture().get()
    }
  }
}