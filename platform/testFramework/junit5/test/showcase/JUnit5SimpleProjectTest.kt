// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

/**
 * Ensures that a project can be disposed quickly right after creation without leaks.
 * Run this test alone to check it.
 */
@TestApplication
class JUnit5SimpleProjectTest {

  private val project = projectFixture()

  @Test
  fun `open after creation`() {
    project.get()
  }
}