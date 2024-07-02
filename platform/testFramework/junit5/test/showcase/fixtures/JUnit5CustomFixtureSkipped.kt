// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase.fixtures

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

@TestOnly
private fun customFixture(): TestFixture<String> = testFixture {
  Assumptions.assumeTrue(false)
  initialized("") {}
}

@TestOnly
private fun TestFixture<String>.customDependentFixture(): TestFixture<Int> = testFixture {
  this@customDependentFixture.init()
  error("This should never be called")
}

@TestApplication
private class JUnit5CustomFixtureSkipped {
  private val fixture = customFixture()
  private val fixtureDep = fixture.customDependentFixture()

  @Test
  fun thisTestMustBeIgnored() {
    fixture.get()
    Assertions.fail<Nothing>("This test must be ignored")
  }

  @Test
  fun thisDepTestMustBeIgnored() {
    fixtureDep.get()
    Assertions.fail<Nothing>("This test must be ignored")
  }
}