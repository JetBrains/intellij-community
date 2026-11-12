// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.junit.jupiter.api.Test

@TestApplication
class Groovy40UnnamedVariableResolveTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_4_0)
  }

  private val resolve by groovyResolveFixture(projectFixture)

  @Test
  fun testResolveInsideVariableDefinition() {
    resolve.resolveByText("""
      def f() {
        def (_) = [1]
        println <caret>_
      }
    """.trimIndent(), GrVariable::class.java)
  }

  @Test
  fun testResolveInsideParameterList() {
    resolve.resolveByText("""
      def f() {
        def x = { _, a ->
            println <caret>_
        }
      }
    """.trimIndent(), GrParameter::class.java)
  }

  @Test
  fun testResolveInsideLambda() {
    resolve.resolveByText("""
      def f() {
        def x = (_) -> println <caret>_
      } 
    """.trimIndent(), GrParameter::class.java)
  }
}