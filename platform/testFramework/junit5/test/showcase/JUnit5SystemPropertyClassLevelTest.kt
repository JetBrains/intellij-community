// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.testFramework.junit5.SystemPropertyClassLevel
import com.intellij.testFramework.junit5.fixture.TestFixtures
import com.intellij.testFramework.junit5.fixture.testFixture
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@SystemPropertyClassLevel("hello-class-level-fixture", "world-class-level-fixture")
@TestFixtures
class JUnit5SystemPropertyClassLevelTest {

  private val fixtureValue by testFixture {
      initialized(System.getProperty("hello-class-level-fixture")) {}
  }

  @Test
  fun `class-level system property covers fixtures`() {
      Assertions.assertEquals("world-class-level-fixture", fixtureValue)
  }
}