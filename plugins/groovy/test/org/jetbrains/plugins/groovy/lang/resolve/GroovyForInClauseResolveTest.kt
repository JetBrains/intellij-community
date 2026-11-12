// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.junit.jupiter.api.Test

@TestApplication
class GroovyForInClauseResolveTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_5_0)
  }

  private val resolve by groovyResolveFixture(projectFixture)

  @Test
  fun testResolveToValueVariable() {
    resolve.resolveByText(
      """
        for(int value in 1..10) {
          println val<caret>ue
        }
      """.trimIndent(),
      GrParameter::class.java
    )
  }

  @Test
  fun testResolveToValueVariableWithIndexVariable() {
    resolve.resolveByText(
      """
        for(int idx, int value in 1..10) {
          println val<caret>ue
        }
      """.trimIndent(),
      GrParameter::class.java
    )
  }

  @Test
  fun testResolveToIndexVariableWithIndexVariable() {
    resolve.resolveByText(
      """
        for(int idx, int value in 1..10) {
          println id<caret>x
        }
      """.trimIndent(),
      GrParameter::class.java
    )
  }
}