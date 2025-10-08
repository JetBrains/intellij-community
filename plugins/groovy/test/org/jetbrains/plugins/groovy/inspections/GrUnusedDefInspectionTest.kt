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

  fun testUnnamedParametersInVariableDeclaration() {
    doTestHighlighting("""
        def (_, _, <warning descr="Assignment is not used">a</warning>, _, _) = [1, 2, 3, 4, 5]
        var (_, _, <warning descr="Assignment is not used">b</warning>, _, _) = [1, 2, 3, 4, 5]
        def (_, _) = [1, 2]
        var (_, _) = [1, 2]
    """.trimIndent())
  }

  fun testUnnamedParametersInLambdaClosures() {
    doTestHighlighting("""
        def <warning descr="Assignment is not used">a</warning> = (_, _, x, _, _) -> x
        def <warning descr="Assignment is not used">b</warning> = { _, _, y, _, _ -> y }
        def <warning descr="Assignment is not used">c</warning> = { _ -> 1 }
        def <warning descr="Assignment is not used">d</warning> = { (_) -> 1 }
    """.trimIndent())
  }

  fun testVariableWithUnderscoreNameIsConsideredToBeUnused() {
    doTestHighlighting("""
      static void a() {
          def <warning descr="Assignment is not used">_</warning> = 1
      }
      
      static void b() {
          if (new Object() instanceof Object <warning descr="Variable is not used">_</warning>) {}
      }
      
      static void c() {
          for(int <warning descr="Assignment is not used">_</warning> = 1;;) {}
      }
      
      static void main(String[] args) {
          a()
          b()
          c()
      }
    """)
  }

  override fun getProjectDescriptor(): LightProjectDescriptor = GROOVY_5_0
}