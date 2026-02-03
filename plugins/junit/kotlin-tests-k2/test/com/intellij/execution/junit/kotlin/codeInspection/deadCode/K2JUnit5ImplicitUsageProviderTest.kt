// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.kotlin.codeInspection.deadCode

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.UnusedSymbolInspection

class K2JUnit5ImplicitUsageProviderTest : KotlinJUnit5ImplicitUsageProviderTest() {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2
  override val inspection: InspectionProfileEntry by lazy { UnusedSymbolInspection() }

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