// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors.GROOVY_4_0
import org.jetbrains.plugins.groovy.LightGroovyTestCase

class GroovyAnnotatorPre50Test: LightGroovyTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = GROOVY_4_0

  fun testPatternVariable() {
    myFixture.configureByText("a.groovy", """
      class A{}
      class B extends A{}
      def foo() {
        A a = new B()
        if (a instanceof B <error descr="Pattern variables inside instanceof expressions are available in Groovy 5.0 or later">b</error>) {
        }
      }
    """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testArrayInitializer() {
    myFixture.configureByText("a.groovy", """
      class A{}
      class B extends A{}
      def foo() {
        def o = new String[][]{}
      
        def a = <error descr="Multi-dimensional array initializers are available in Groovy 5.0 or later">new String[][]{{}}</error>
        
        def b = <error descr="Multi-dimensional array initializers are available in Groovy 5.0 or later">new String[][]{{"foo"}}</error>
      }
    """.trimIndent())
    myFixture.testHighlighting()
  }

  fun testIndexVariableInForeachLoop() {
    myFixture.configureByText("a.groovy", """
      def foo() {
        for (<error descr="Index variables in foreach loops are available in Groovy 5.0 or later">int idx</error>, int i in [1, 2, 3]) {
          println(idx)
        }
        
        for (<error descr="Index variables in foreach loops are available in Groovy 5.0 or later">var idx</error>, int i : [1, 2, 3]) {
          println(idx)
        }
      }
    """)
    myFixture.testHighlighting()
  }
}