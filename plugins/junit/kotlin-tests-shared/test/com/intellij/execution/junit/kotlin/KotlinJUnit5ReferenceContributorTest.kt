// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin

import com.intellij.junit.testFramework.JUnit5ReferenceContributorTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KotlinJUnit5ReferenceContributorTest : JUnit5ReferenceContributorTestBase(), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
  }

  fun `test resolve to enum source value`() {
    myFixture.assertResolvableReference(JvmLanguage.KOTLIN, """
      enum class Foo { AAA, BBB }
      
      class ExampleTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(
          value = F<caret>oo::class
        )
        fun valid() {}
      }
    """.trimIndent())
  }

  fun `test resolve to enum source with a single name`() {
    myFixture.assertResolvableReference(JvmLanguage.KOTLIN, """
      enum class Foo { AAA, BBB }
      
      class ExampleTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(
          value = Foo::class, 
          names = ["AAA", "B<caret>BB"]
        )
        fun valid() {}
      }
    """.trimIndent())
  }

  fun `test resolve to enum source with multiple names`() {
    myFixture.assertResolvableReference(JvmLanguage.KOTLIN, """
      enum class Foo { AAA, BBB }
      
      class ExampleTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(
          value = Foo::class, 
          names = ["AAA", "B<caret>BB"]
        )
        fun valid() {}
      }
    """.trimIndent())
  }

  fun `test resolve to source field`() {
    myFixture.assertResolvableReference(JvmLanguage.KOTLIN, """
      import org.junit.jupiter.params.ParameterizedTest
      import org.junit.jupiter.params.provider.FieldSource

      class ParameterizedFieldSourceTestsDemo {

        @ParameterizedTest
        @FieldSource("aaa", "bbb<caret>2")
        fun testWithProvider(abc: String) {}

        companion object {
          @JvmField
          val aaa: List<String> = listOf("something1", "something2")

          @JvmField
          val bbb2: List<String> = listOf("something1", "something2")
        }
      }
    """.trimIndent())
  }
}