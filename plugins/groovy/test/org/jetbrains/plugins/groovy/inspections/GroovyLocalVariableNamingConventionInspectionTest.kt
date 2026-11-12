// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.codeInspection.naming.GroovyLocalVariableNamingConventionInspection
import org.jetbrains.plugins.groovy.lang.highlighting.grHighlightingFixture
import org.junit.jupiter.api.Test

@TestApplication
class GroovyLocalVariableNamingConventionInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_5_0)
  }

  private val highlighting by grHighlightingFixture(projectFixture, customInspections = { arrayOf(GroovyLocalVariableNamingConventionInspection()) })

  @Test
  fun testUnnamedVariable() {
    highlighting.doTestHighlighting("""
      def f() {
         var (_, _) = [1, 2]
         var <warning descr="Local variable name '_' is too short">_</warning> = 1
      }
    """.trimIndent()
    )
  }
}