// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase.fixtures

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


@TestOnly
private fun customFixture(prefix: String): TestFixture<String> = testFixture {
  val text = "$prefix text"
  initialized(text) {
    // clean up text
  }
}

@TestApplication
class JUnit5CustomFixture {
  private val fixture = customFixture("foo")

  @Test
  fun textCustomFixture() {
    Assertions.assertEquals("foo text", fixture.get())
  }
}