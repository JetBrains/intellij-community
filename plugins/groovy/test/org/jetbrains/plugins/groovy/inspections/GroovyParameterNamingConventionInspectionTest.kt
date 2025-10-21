// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInspection.naming.GroovyParameterNamingConventionInspection
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

class GroovyParameterNamingConventionInspectionTest : GrHighlightingTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_5_0
  }

  override fun getCustomInspections(): Array<out InspectionProfileEntry> {
    return arrayOf(GroovyParameterNamingConventionInspection())
  }

  fun testUnnamedVariable() {
    doTestHighlighting("""
      def f() {
         def closure = { _ -> println 1 } 
         def lambda = _ -> println 1
         def lambdaInParentheses = (_) -> println 1
      }
    """.trimIndent()
    )
  }
}