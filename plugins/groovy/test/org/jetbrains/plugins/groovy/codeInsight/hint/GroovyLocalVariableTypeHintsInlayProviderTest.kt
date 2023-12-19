// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInsight.hint.types.GroovyLocalVariableTypeHintsInlayProvider

class GroovyLocalVariableTypeHintsInlayProviderTest : DeclarativeInlayHintsProviderTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_3_0
  }

  private fun testTypeHints(@Language("Groovy") text: String) {
    doTestProvider("test.groovy", text, GroovyLocalVariableTypeHintsInlayProvider())
  }

  fun `test basic cases`() {
    val text = """
      def x/*<# : |Integer #>*/ = 1
      def y/*<# : |String #>*/ = "abc"
    """.trimIndent()
    testTypeHints(text)
  }

  fun `test no type hint for object or null`() {
    testTypeHints("""
      def x = null
      def foo() {}
      def y = foo()
    """.trimIndent())
  }

  fun `test no type hint for casted expression`() {
    testTypeHints("""
      def x = 1 as Number
      def y = (Number)1
    """.trimIndent())
  }

  fun `test no type hint for constructor calls`() {
    testTypeHints("""
      def x = new File()
    """.trimIndent()
    )
  }

  fun `test var keyword`() {
    testTypeHints("""
      var x/*<# : |Integer #>*/ = 1
    """.trimIndent())
  }

  fun `test tuples`() {
    testTypeHints("""
    def (a/*<# : |Integer #>*/, b/*<# : |String #>*/) = new Tuple2<>(1, "")
    """.trimIndent())
  }

  fun `test no type hint for variable with explicit type`() {
    testTypeHints("""
      String s = ""
    """.trimIndent())
  }

  fun `test no type hints for fields`() {
    testTypeHints("""
      class A {
        def foo = 1
      }
    """.trimIndent())
  }
}
