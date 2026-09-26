// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.support.ParameterDeclarations
import java.util.stream.Stream

private fun stringFixture(text: String): TestFixture<String> = testFixture {
  initialized(text) {}
}

@TestApplication
class JUnit5NestedFixtureTest {
  companion object {
    private const val OUTER_MOST = "JUnit5NestedFixtureTest"
    private const val NESTED_ONE = "NestedInnerClass"
  }

  private val outerMost by stringFixture(OUTER_MOST)

  @Nested
  inner class NestedInnerClass {
    private val nestedOne by stringFixture(NESTED_ONE)

    @Test
    fun testParentClassFixtureIsInitialized() {
      assertEquals(outerMost, OUTER_MOST)
    }

    @Nested
    inner class DoublyNestedInnerClass {
      @Test
      fun testArbitrarilyNestedClass() {
        assertEquals(nestedOne, NESTED_ONE)
      }
    }
  }

  private class Params : ArgumentsProvider {
    override fun provideArguments(parameters: ParameterDeclarations?, context: ExtensionContext?): Stream<out Arguments?>? {
      return Stream.of(Arguments.of(1))
    }
  }

  @Nested
  @ParameterizedClass
  @ArgumentsSource(Params::class)
  inner class NestedParametrizedClass(@Suppress("unused") n: Int) {
    @Test
    fun testParametrizedClassInit() {
      assertEquals(outerMost, OUTER_MOST)
    }
  }
}