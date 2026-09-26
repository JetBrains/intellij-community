// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter

class Groovy40UnnamedVariableResolveTest : GroovyResolveTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_4_0
  }

  fun testResolveInsideVariableDefinition() {
    resolveByText("""
      def f() {
        def (_) = [1]
        println <caret>_
      }
    """.trimIndent(), GrVariable::class.java)
  }

  fun testResolveInsideParameterList() {
    resolveByText("""
      def f() {
        def x = { _, a ->
            println <caret>_
        }
      }
    """.trimIndent(), GrParameter::class.java)
  }

  fun testResolveInsideLambda() {
    resolveByText("""
      def f() {
        def x = (_) -> println <caret>_
      } 
    """.trimIndent(), GrParameter::class.java)
  }
}