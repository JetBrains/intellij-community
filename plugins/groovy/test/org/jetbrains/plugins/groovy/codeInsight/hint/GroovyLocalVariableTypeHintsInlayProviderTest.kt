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

}