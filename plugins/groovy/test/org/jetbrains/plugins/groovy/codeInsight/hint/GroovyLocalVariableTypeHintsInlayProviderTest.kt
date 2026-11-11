// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestUtil.doTestProvider
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInsight.hint.types.GroovyLocalVariableTypeHintsInlayProvider
import org.junit.jupiter.api.Test

@TestApplication
class GroovyLocalVariableTypeHintsInlayProviderTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_3_0)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  private fun testTypeHints(@Language("Groovy") text: String) {
    doTestProvider(myFixture, "test.groovy", text, GroovyLocalVariableTypeHintsInlayProvider())
  }

  @Test
  fun `test basic cases`() {
    val text = """
      def x/*<# : |[java.lang.Integer:java.fqn.class]Integer #>*/ = 1
      def y/*<# : |[java.lang.String:java.fqn.class]String #>*/ = "abc"
    """.trimIndent()
    testTypeHints(text)
  }

  @Test
  fun `test no type hint for object or null`() {
    testTypeHints("""
      def x = null
      def foo() {}
      def y = foo()
    """.trimIndent())
  }

  @Test
  fun `test no type hint for casted expression`() {
    testTypeHints("""
      def x = 1 as Number
      def y = (Number)1
    """.trimIndent())
  }

  @Test
  fun `test no type hint for constructor calls`() {
    testTypeHints("""
      def x = new File()
    """.trimIndent()
    )
  }

  @Test
  fun `test var keyword`() {
    testTypeHints("""
      var x/*<# : |[java.lang.Integer:java.fqn.class]Integer #>*/ = 1
    """.trimIndent())
  }

  @Test
  fun `test tuples`() {
    testTypeHints("""
    def (a/*<# : |[java.lang.Integer:java.fqn.class]Integer #>*/, b/*<# : |[java.lang.String:java.fqn.class]String #>*/) = new Tuple2<>(1, "")
    """.trimIndent())
  }

  @Test
  fun `test no type hint for variable with explicit type`() {
    testTypeHints("""
      String s = ""
    """.trimIndent())
  }

  @Test
  fun `test no type hints for fields`() {
    testTypeHints("""
      class A {
        def foo = 1
      }
    """.trimIndent())
  }
}
