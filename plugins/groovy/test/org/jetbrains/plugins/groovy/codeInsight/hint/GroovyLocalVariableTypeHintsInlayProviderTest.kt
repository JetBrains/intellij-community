// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.codeInsight.hints.NoSettings
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInsight.hint.types.GroovyLocalVariableTypeHintsInlayProvider

class GroovyLocalVariableTypeHintsInlayProviderTest : InlayHintsProviderTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_3_0
  }

  private fun testTypeHints(text: String) {
    testProvider("test.groovy", text, GroovyLocalVariableTypeHintsInlayProvider(), NoSettings())
  }

  fun `test basic cases`() {
    val text = """
      def x<# [:  Integer] #> = 1
      def y<# [:  String] #> = "abc"
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
      def x<# [:  Integer] #> = 1
    """.trimIndent())
  }

  fun `test tuples`() {
    testTypeHints("""
    def (a<# [:  Integer] #>, b<# [:  String] #>) = new Tuple2<>(1, "")
    """.trimIndent())
  }

}