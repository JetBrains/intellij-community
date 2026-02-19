// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInspection.naming.GroovyLocalVariableNamingConventionInspection
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

class GroovyLocalVariableNamingConventionInspectionTest : GrHighlightingTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_5_0
  }

  override fun getCustomInspections(): Array<out InspectionProfileEntry> {
    return arrayOf(GroovyLocalVariableNamingConventionInspection())
  }

  fun testUnnamedVariable() {
    doTestHighlighting("""
      def f() {
         var (_, _) = [1, 2]
         var <warning descr="Local variable name '_' is too short">_</warning> = 1
      }
    """.trimIndent()
    )
  }
}