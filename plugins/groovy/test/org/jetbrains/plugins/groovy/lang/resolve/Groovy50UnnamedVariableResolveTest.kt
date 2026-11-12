// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.junit.jupiter.api.Test

@TestApplication
class Groovy50UnnamedVariableResolveTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_5_0)
  }

  private val resolve by groovyResolveFixture(projectFixture)

  @Test
  fun testNoResolveInsideVariableDefinition() {
    resolve.resolveByText<GrVariable>("""
      def f() {
        def (_) = [1]
        println <caret>_
      }
    """.trimIndent(), null)
  }

  @Test
  fun testNoResolveInsideParameterList() {
    resolve.resolveByText<GrVariable>("""
      def f() {
        def x = { _, a ->
            println <caret>_
        }
      }
    """.trimIndent(), null)
  }

  @Test
  fun testNoResolveInsideLambda() {
    resolve.resolveByText<GrVariable>("""
      def f() {
        def x = (_) -> println <caret>_
      } 
    """.trimIndent(), null)
  }

  @Test
  fun testResolveInClosureWithOuter() {
    resolve.resolveByText("""
      def f() {
        def _ = 1
        def x = {  _ -> println <caret>_ }
      } 
    """, GrVariable::class.java)
  }

  @Test
  fun testResolveInLambdaWithOuter() {
    resolve.resolveByText("""
      def f() {
        def _ = 1
        def y = (_) -> <caret>_
      } 
    """, GrVariable::class.java)
  }

  @Test
  fun testResolveInVariableDeclarationWithOuter() {
    resolve.resolveByText("""
      def f() {
        def _ = 1
        def (_, _) = [1, 2]
        println <caret>_
      } 
    """, GrVariable::class.java)
  }
}