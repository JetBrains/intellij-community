// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString

@TestFixtures
internal class JUnit5TempFixtureTest {
  private val tempDir by tempPathFixture()

  @Test
  fun `test with spaces`(): Unit = timeoutRunBlocking {
    checkTempDir("withspaces")
  }

  @ParameterizedTest
  @ValueSource(strings = [":foo:", "/foo/\\", "(foo)"])
  fun `paramterized test`(@Suppress("UNUSED_PARAMETER") /*to make test parametrized*/ str: String): Unit = timeoutRunBlocking {
    checkTempDir("foo")
  }

  private fun checkTempDir(expectedToContaint: String) {
    assertThat(tempDir.fileName.pathString).contains(expectedToContaint)
    tempDir.listDirectoryEntries()
  }

}
