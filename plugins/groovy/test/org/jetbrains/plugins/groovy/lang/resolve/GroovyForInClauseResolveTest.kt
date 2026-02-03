// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter

class GroovyForInClauseResolveTest : GroovyResolveTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_5_0
  }

  fun testResolveToValueVariable() {
    resolveByText(
      """
        for(int value in 1..10) {
          println val<caret>ue
        }
      """.trimIndent(),
      GrParameter::class.java
    )
  }

  fun testResolveToValueVariableWithIndexVariable() {
    resolveByText(
      """
        for(int idx, int value in 1..10) {
          println val<caret>ue
        }
      """.trimIndent(),
      GrParameter::class.java
    )
  }

  fun testResolveToIndexVariableWithIndexVariable() {
    resolveByText(
      """
        for(int idx, int value in 1..10) {
          println id<caret>x
        }
      """.trimIndent(),
      GrParameter::class.java
    )
  }
}