// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestFixtures
class FixtureDependencyTest {

  @Suppress("unused")
  private val fixture by testFixture("dependent") {
    events += DEPENDENT_INIT
    initialized(Unit) {
      events += DEPENDENT_TEAR_DOWN
    }
  }.dependsOn(testFixture("dependency") {
    events += DEPENDENCY_INIT
    initialized(Unit) {
      events += DEPENDENCY_TEAR_DOWN
    }
  })

  @BeforeEach
  fun beforeEach() {
    assertEvents(DEPENDENCY_INIT, DEPENDENT_INIT)
  }

  @AfterEach
  fun afterEach() {
    assertEvents(DEPENDENCY_INIT, DEPENDENT_INIT)
  }

  @Test
  fun `dependsOn initializes dependency before dependent`() {
    assertEvents(DEPENDENCY_INIT, DEPENDENT_INIT)
  }

  companion object {

    private const val DEPENDENCY_INIT = "dependency init"
    private const val DEPENDENCY_TEAR_DOWN = "dependency tearDown"
    private const val DEPENDENT_INIT = "dependent init"
    private const val DEPENDENT_TEAR_DOWN = "dependent tearDown"

    private val events = ArrayList<String>()

    fun assertEvents(vararg expected: String) {
      assertEquals(expected.toList(), events)
    }

    @BeforeAll
    @JvmStatic
    fun beforeAll() {
      assertEvents()
    }

    @AfterAll
    @JvmStatic
    fun afterAll() {
      assertEvents(
        DEPENDENCY_INIT,
        DEPENDENT_INIT,
        DEPENDENT_TEAR_DOWN,
        DEPENDENCY_TEAR_DOWN,
      )
    }
  }
}
