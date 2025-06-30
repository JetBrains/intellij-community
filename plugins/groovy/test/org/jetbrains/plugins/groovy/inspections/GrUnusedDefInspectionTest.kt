// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors.GROOVY_5_0
import org.jetbrains.plugins.groovy.codeInspection.unusedDef.UnusedDefInspection
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingTestBase

class GrUnusedDefInspectionTest : GrHighlightingTestBase() {
  override fun getCustomInspections(): Array<out InspectionProfileEntry?> {
    return arrayOf(UnusedDefInspection())
  }

  fun testUnusedInstanceofPatternVariable() {
    doTestHighlighting("""
      class X {
        static class A {}
        def foo(Object o) {
          if (o instanceof A <warning descr="Variable is not used">someName</warning>) {}
        }
      }
    """.trimIndent())
  }

  fun testUsedInstanceofPatternVariable() {
    doTestHighlighting("""
      class X {
        static class A {}
        def foo(Object o) {
          if (o instanceof A someName) {
              println someName
          }
        }
      }
    """.trimIndent())
  }

  fun testUsedInstanceOfVariableAfterOr() {
    doTestHighlighting("""
      class X {
        static class A {}
        def foo(Object o) {
          if (o instanceof A someName || someName) {
          }
        }
      }
    """.trimIndent())
  }

  fun testUsedInstanceOfVariableAfterAndWithNegation() {
    doTestHighlighting("""
      class X {
        static class A {}
        def foo(Object o) {
          if (!(o instanceof A someName) && someName) {
          }
        }
      }
    """.trimIndent())
  }


  fun testUsedInstanceOfVariableWithNegation() {
    doTestHighlighting("""
      class X {
        static class A {}
        def foo(Object o) {
          if (!(o instanceof A someName)) {
            someName
          }
        }
      }
    """.trimIndent())
  }

  fun testUnusedPatternVariableInConditionalExpression() {
    doTestHighlighting("""
      class X {
        static class A {}
        def foo(Object o) {
          boolean t = a instanceof A <warning descr="Variable is not used">someName</warning>
          println t
        }
      }
    """.trimIndent())
  }

  fun testUsedInstanceOfVariableWithForLoopCondition() {
    doTestHighlighting("""
      class X {
        static class A {}
        def foo(Object o) {
          for (int i = 0; o instanceof A someName; i++) {
            someName
          }
        }
      }
    """)
  }

  fun testUnusedInstanceOfVariableWithForLoopInitialization() {
    doTestHighlighting("""
      class X {
        static class A {}
        def foo(Object o) {
          for (boolean t = a instanceof A <warning descr="Variable is not used">someName</warning>; ;t++) {
          }
        }
      }
    """)
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = GROOVY_5_0
}