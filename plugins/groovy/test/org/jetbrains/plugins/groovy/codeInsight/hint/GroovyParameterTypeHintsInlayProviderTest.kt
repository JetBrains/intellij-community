// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

class GroovyParameterTypeHintsInlayProviderTest : InlayHintsProviderTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_LATEST
  }

  private fun testTypeHints(text: String, settings:
  GroovyParameterTypeHintsInlayProvider.Settings = GroovyParameterTypeHintsInlayProvider.Settings(showInferredParameterTypes = true,
                                                                                                  showTypeParameterList = true)) {
    testProvider("test.groovy", text, GroovyParameterTypeHintsInlayProvider(), settings)
  }

  fun testSingleType() {
    val text = """
def foo(<# [Integer  ] #>a) {}

foo(1)
    """.trimIndent()
    testTypeHints(text)
  }

  fun testWildcard() {
    val text = """
def foo(<# [[List < [? [ super  Number]] >]  ] #>a) {
  a.add(1)
}

foo(null as List<Object>)
foo(null as List<Number>)
    """.trimIndent()
    testTypeHints(text)
  }

  fun testTypeParameters() {
    val text = """
def<# [< V0 >] #> foo(<# [[List < V0 >]  ] #>a, <# [[List < [? [ extends  V0]] >]  ] #>b) {
  a.add(b[0])
}

foo([1], [1])
foo(['q'], ['q'])
    """.trimIndent()
    testTypeHints(text)
  }

  fun testClosure() {
    val text = """
def<# [< [X0 extends  A] >] #> foo(<# [X0  ] #>a, <# [[Closure < Object >]  ] #>c) {
  c(a)
}

interface A{def foo()}

foo(null as A) {
  it.foo()
}
    """.trimIndent()
    testTypeHints(text)
  }

}