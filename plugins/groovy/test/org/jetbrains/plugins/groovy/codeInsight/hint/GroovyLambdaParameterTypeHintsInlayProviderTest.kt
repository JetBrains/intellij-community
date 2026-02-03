// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInsight.hint.types.GroovyLambdaParameterTypeHintsInlayProvider

class GroovyLambdaParameterTypeHintsInlayProviderTest : DeclarativeInlayHintsProviderTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = GroovyProjectDescriptors.GROOVY_3_0

  private fun doTest(@Language("Groovy") text: String) {
    doTestProvider("test.groovy", text, GroovyLambdaParameterTypeHintsInlayProvider())
  }

  fun `test single named argument`() {
    val text = """
    import groovy.transform.stc.ClosureParams
    import groovy.transform.stc.SimpleType
    
    def foo(@ClosureParams(value=SimpleType, options=['java.lang.Integer'])Closure a) {}
    def bar(@ClosureParams(value=SimpleType, options=['java.lang.Integer'])Closure b) {}
    
    foo { /*<# [java.lang.Integer:java.fqn.class]Integer|  #>*/a ->
    bar { /*<# [java.lang.Integer:java.fqn.class]Integer|  #>*/b ->
      }
    }
    """.trimIndent()
    doTest(text)
  }

  fun `test multiple named arguments`() {
    val text = """
      import groovy.transform.stc.ClosureParams
      import groovy.transform.stc.SimpleType

      def fun(@ClosureParams(value= SimpleType, options=['java.lang.Integer', 'java.lang.String'])Closure a) {}
      
      fun { /*<# [java.lang.Integer:java.fqn.class]Integer|  #>*/a, /*<# [java.lang.String:java.fqn.class]String|  #>*/b -> }
    """.trimIndent()
    doTest(text)
  }

  fun `test single implicit argument`() {
    val text = """
      import groovy.transform.stc.ClosureParams
      import groovy.transform.stc.SimpleType

      def foo(@ClosureParams(value= SimpleType, options=['java.lang.Integer'])Closure a) {}

      foo {/*<# [java.lang.Integer:java.fqn.class]Integer| it ->  #>*/ }
    """.trimIndent()
    doTest(text)
  }

  fun `test no inlay hints for implicit arguments when multiple parameters`() {
    val text = """
      import groovy.transform.stc.ClosureParams
      import groovy.transform.stc.SimpleType

      def foo(@ClosureParams(value= SimpleType, options=['java.lang.Integer', 'java.lang.String'])Closure a) {}

      foo {
       }
    """.trimIndent()
    doTest(text)
  }
}