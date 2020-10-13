// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter

class GroovyParameterTypeHintsInlayProviderTest : InlayHintsProviderTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_3_0
  }

  private fun testTypeHints(text: String, settings:
  GroovyParameterTypeHintsInlayProvider.Settings = GroovyParameterTypeHintsInlayProvider.Settings(showInferredParameterTypes = true,
                                                                                                  showTypeParameterList = true)) {
    testProvider("test.groovy", text, GroovyParameterTypeHintsInlayProvider(), settings)
  }

  override fun setUp() {
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(true)
    super.setUp()
  }

  override fun tearDown() {
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).resetToDefault()
    super.tearDown()
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
def<# [< T >] #> foo(<# [[ArrayList < T >]  ] #>a, <# [[ArrayList < [? [ extends  T]] >]  ] #>b) {
  a.add(b[0])
}

foo([1], [1])
foo(['q'], ['q'])
    """.trimIndent()
    testTypeHints(text)
  }

  fun testClosure() {
    val text = """
def<# [< [T extends  A] >] #> foo(<# [T  ] #>a, <# [[Closure < [?  ] >]  ] #>c) {
  c(a)
}

interface A{def foo()}

foo(null as A) {<# [A  it -> ] #>
  it.foo()
}
    """.trimIndent()
    testTypeHints(text)
  }


  fun testInsideClosure() {
    val text = """
def foo(<# [Integer  ] #>arg, <# [[Closure < [?  ] >]  ] #>closure) {
  closure(arg)
}

foo(1) { <# [Integer  ] #>a -> a.byteValue() }
    """.trimIndent()
    testTypeHints(text)
  }

  fun testInsideLambda() {
    val text = """
def foo(<# [Integer  ] #>arg, <# [[Closure < [?  ] >]  ] #>closure) {
  closure(arg)
}

foo(1, <# [Integer  ] #>a -> a.byteValue())
    """.trimIndent()
    testTypeHints(text)
  }

  fun testNoTypeHintsWithoutTypeInformation() {
    val text = """
def foo(x) {}
    """.trimIndent()
    testTypeHints(text)
  }

  fun testNestedClosureBlockWithUnresolvableVariables() {
    val text = """
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
def foo(@ClosureParams(value=SimpleType, options=['java.lang.Integer'])Closure a) {}
def bar(@ClosureParams(value=SimpleType, options=['java.lang.Integer'])Closure b) {}
foo { <# [Integer  ] #>a ->
  bar { <# [Integer  ] #>b ->
    for (x in y){}
  }
}
    """.trimIndent()
    testTypeHints(text)
  }
}