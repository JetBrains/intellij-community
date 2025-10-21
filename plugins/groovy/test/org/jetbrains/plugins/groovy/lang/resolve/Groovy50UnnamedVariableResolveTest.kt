// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable

class Groovy50UnnamedVariableResolveTest : GroovyResolveTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_5_0
  }

  fun testNoResolveInsideVariableDefinition() {
    resolveByText<GrVariable>("""
      def f() {
        def (_) = [1]
        println <caret>_
      }
    """.trimIndent(), null)
  }

  fun testNoResolveInsideParameterList() {
    resolveByText<GrVariable>("""
      def f() {
        def x = { _, a ->
            println <caret>_
        }
      }
    """.trimIndent(), null)
  }

  fun testNoResolveInsideLambda() {
    resolveByText<GrVariable>("""
      def f() {
        def x = (_) -> println <caret>_
      } 
    """.trimIndent(), null)
  }


  fun testResolveInClosureWithOuter() {
    resolveByText("""
      def f() {
        def _ = 1
        def x = {  _ -> println <caret>_ }
      } 
    """, GrVariable::class.java)
  }


  fun testResolveInLambdaWithOuter() {
    resolveByText("""
      def f() {
        def _ = 1
        def y = (_) -> <caret>_
      } 
    """, GrVariable::class.java)
  }


  fun testResolveInVariableDeclarationWithOuter() {
    resolveByText("""
      def f() {
        def _ = 1
        def (_, _) = [1, 2]
        println <caret>_
      } 
    """, GrVariable::class.java)
  }
}