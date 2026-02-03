// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

class Gr50ConvertParameterToMapEntryTest : GrIntentionTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_5_0
  }

  fun testUnnamedParameterInLambda() {
    doAntiTest("""
      def f() {
       def a = (<caret>_) -> println
      }
    """.trimIndent(), "Convert parameter to map entry")
  }

  fun testUnnamedParameterInClosure() {
    doAntiTest("""
      def f() {
       def a = { _<caret> -> println }
      }
    """.trimIndent(), "Convert parameter to map entry")
  }
}