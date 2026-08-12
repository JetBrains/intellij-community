// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.showcase

import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.testFramework.junit5.fixture.testFixture
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junitpioneer.jupiter.cartesian.CartesianTest
import java.util.concurrent.atomic.AtomicInteger

/**
 * Regression test for instance-level fixtures leaking in a [ParameterizedClass] whose test methods are
 * [org.junit.jupiter.api.TestTemplate]s ([ParameterizedTest] / [CartesianTest]).
 *
 * The eel comes from the class constructor, so fixture initialization used to happen while the test class was being
 * constructed, where the only available extension context is the *class* one: shared by every class-template
 * invocation and never paired with an `after` callback. A second, empty scope created for the template method then
 * shadowed it in the store hierarchy, so `afterEach` cancelled the wrong scope and no tear-down ever ran.
 *
 * A counting fixture is used on purpose instead of [com.intellij.testFramework.junit5.fixture.projectFixture]: it
 * fails right here on any machine, whereas a leaked project only surfaces much later, in `_LastInSuiteTest`.
 */
@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
@ParameterizedClass
internal class EelParametrizedClassFixtureTearDownTest(val eelHolder: EelHolder) {
  @BeforeEach
  fun setUp() {
    eelHolder.type
  }

  private companion object {
    val initialized = AtomicInteger()
    val tornDown = AtomicInteger()

    /**
     * Invoked once for the whole class template, after every invocation's `afterEach` and before the fixture
     * extension's own `afterAll`, so both counters are final and cumulative over all eel variants here.
     */
    @JvmStatic
    @AfterAll
    fun `every instance fixture was torn down`() {
      assertTrue(initialized.get() > 0, "the instance fixture was never initialized")
      assertEquals(initialized.get(), tornDown.get(), "the instance fixture was not torn down after every test")
    }
  }

  private val counting = testFixture("counting") {
    initialized.incrementAndGet()
    initialized(Unit) {
      tornDown.incrementAndGet()
    }
  }

  @Test
  fun plainTest() {
    counting.get()
  }

  @ParameterizedTest
  @ValueSource(ints = [1, 2])
  fun parameterizedTest(@Suppress("unused") i: Int) {
    counting.get()
  }

  @CartesianTest
  fun cartesianTest(@Suppress("unused") @CartesianTest.Values(ints = [1, 2]) i: Int) {
    counting.get()
  }
}
