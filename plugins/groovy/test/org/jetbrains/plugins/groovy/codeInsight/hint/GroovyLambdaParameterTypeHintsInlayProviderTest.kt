// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.hint

import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestUtil.doTestProvider
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInsight.hint.types.GroovyLambdaParameterTypeHintsInlayProvider
import org.junit.jupiter.api.Test

@TestApplication
class GroovyLambdaParameterTypeHintsInlayProviderTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_3_0)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  private fun doTest(@Language("Groovy") text: String) {
    doTestProvider(myFixture, "test.groovy", text, GroovyLambdaParameterTypeHintsInlayProvider())
  }

  @Test
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

  @Test
  fun `test multiple named arguments`() {
    val text = """
      import groovy.transform.stc.ClosureParams
      import groovy.transform.stc.SimpleType

      def fun(@ClosureParams(value= SimpleType, options=['java.lang.Integer', 'java.lang.String'])Closure a) {}
      
      fun { /*<# [java.lang.Integer:java.fqn.class]Integer|  #>*/a, /*<# [java.lang.String:java.fqn.class]String|  #>*/b -> }
    """.trimIndent()
    doTest(text)
  }

  @Test
  fun `test single implicit argument`() {
    val text = """
      import groovy.transform.stc.ClosureParams
      import groovy.transform.stc.SimpleType

      def foo(@ClosureParams(value= SimpleType, options=['java.lang.Integer'])Closure a) {}

      foo {/*<# [java.lang.Integer:java.fqn.class]Integer| it ->  #>*/ }
    """.trimIndent()
    doTest(text)
  }

  @Test
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