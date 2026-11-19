// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection.deadCode

import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.kotlin.idea.codeInsight.inspections.UnusedSymbolInspection
import org.junit.jupiter.api.Test

@TestApplication
class K2JUnit5ImplicitUsageProviderTest : KotlinJUnit5ImplicitUsageProviderTest({ UnusedSymbolInspection() }) {
  @Test
  fun `test usage of method source with property name`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import java.util.stream.*
      
      class MyTest {
        @set:org.junit.jupiter.params.ParameterizedTest
        @set:org.junit.jupiter.params.provider.MethodSource("bar")
        var foo: String = "x"
          set(value) {
              field = value
              println(value)
          }
        
        companion object {
          @JvmStatic
          private fun <warning descr="Function \"foo\" is never used">foo</warning>() = Stream.of("")
          
          @JvmStatic
          private fun bar() = Stream.of("")
        }
      }
    """.trimIndent())
  }
}