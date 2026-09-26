// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.intentions

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors

class Gr40ConvertParameterToMapEntryTest : GrIntentionTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor {
    return GroovyProjectDescriptors.GROOVY_4_0
  }

  fun testUnnamedParameterInLambda() {
    doTextTest("""
      def f() {
       def a = (<caret>_) -> println 
       a(1)
      }
    """.trimIndent(), "Convert parameter to map entry",
               """
       def f() {
        def a = (Map attrs) -> println 
        a(1)
       }
       """.trimIndent())
  }

  fun testUnnamedParameterInClosure() {
    doTextTest("""
      def f() {
        def a = { _<caret> -> println }
        a(1)
      }
    """.trimIndent(), "Convert parameter to map entry",
               """
      def f() {
        def a = { Map attrs -> println }
        a(_: 1)
      }
    """.trimIndent())
  }
}