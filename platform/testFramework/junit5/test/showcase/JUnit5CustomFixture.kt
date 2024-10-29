// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.fail
import org.opentest4j.TestAbortedException

@TestApplication
class JUnit5CustomFixture {

  @Nested
  inner class ExampleFixture {

    private val fixture1 = customFixture("foo")
    private val fixture2 = customFixture("bar")

    @Test
    fun test() {
      assertEquals("foo text", fixture1.get())
      assertEquals("bar text", fixture2.get())
    }
  }

  @Nested
  inner class FixtureWithFailedAssumption {

    private val fixture = testFixture {
      assumeTrue(false)
      initialized(Unit) {}
    }

    @Suppress("unused")
    private val dependentFixture = testFixture {
      assertThrows<TestAbortedException> {
        fixture.init()
      }
      initialized(Unit) {}
    }

    @Test
    fun `test is skipped`() {
      fail("Test should be skipped")
    }
  }
}

@TestOnly
private fun customFixture(prefix: String): TestFixture<String> = testFixture {
  val text = "$prefix text"
  initialized(text) {
    // clean up text
  }
}
