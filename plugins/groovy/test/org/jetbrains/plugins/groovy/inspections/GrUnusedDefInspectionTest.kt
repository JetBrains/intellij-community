// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.inspections

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors.GROOVY_5_0
import org.jetbrains.plugins.groovy.codeInspection.unusedDef.UnusedDefInspection
import org.jetbrains.plugins.groovy.lang.highlighting.grHighlightingFixture
import org.junit.jupiter.api.Test

@TestApplication
class GrUnusedDefInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GROOVY_5_0)
  }

  private val highlighting by grHighlightingFixture(projectFixture, customInspections = { arrayOf(UnusedDefInspection()) })

  @Test
  fun testUnusedInstanceofPatternVariable() {
    highlighting.doTestHighlighting("""
      class X {
        static class A {}
        def foo(Object o) {
          if (o instanceof A <warning descr="Variable is not used">someName</warning>) {}
        }
      }
    """.trimIndent())
  }

  @Test
  fun testUsedInstanceofPatternVariable() {
    highlighting.doTestHighlighting("""
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

  @Test
  fun testUsedInstanceOfVariableAfterOr() {
    highlighting.doTestHighlighting("""
      class X {
        static class A {}
        def foo(Object o) {
          if (o instanceof A someName || someName) {
          }
        }
      }
    """.trimIndent())
  }

  @Test
  fun testUsedInstanceOfVariableAfterAndWithNegation() {
    highlighting.doTestHighlighting("""
      class X {
        static class A {}
        def foo(Object o) {
          if (!(o instanceof A someName) && someName) {
          }
        }
      }
    """.trimIndent())
  }

  @Test
  fun testUsedInstanceOfVariableWithNegation() {
    highlighting.doTestHighlighting("""
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

  @Test
  fun testUnusedPatternVariableInConditionalExpression() {
    highlighting.doTestHighlighting("""
      class X {
        static class A {}
        def foo(Object o) {
          boolean t = a instanceof A <warning descr="Variable is not used">someName</warning>
          println t
        }
      }
    """.trimIndent())
  }

  @Test
  fun testUsedInstanceOfVariableWithForLoopCondition() {
    highlighting.doTestHighlighting("""
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

  @Test
  fun testUnusedInstanceOfVariableWithForLoopInitialization() {
    highlighting.doTestHighlighting("""
      class X {
        static class A {}
        def foo(Object o) {
          for (boolean t = a instanceof A <warning descr="Variable is not used">someName</warning>; ;t++) {
          }
        }
      }
    """)
  }

  @Test
  fun testUnnamedParametersInVariableDeclaration() {
    highlighting.doTestHighlighting("""
        def (_, _, <warning descr="Assignment is not used">a</warning>, _, _) = [1, 2, 3, 4, 5]
        var (_, _, <warning descr="Assignment is not used">b</warning>, _, _) = [1, 2, 3, 4, 5]
        def (_, _) = [1, 2]
        var (_, _) = [1, 2]
    """.trimIndent())
  }

  @Test
  fun testUnnamedParametersInLambdaClosures() {
    highlighting.doTestHighlighting("""
        def <warning descr="Assignment is not used">a</warning> = (_, _, x, _, _) -> x
        def <warning descr="Assignment is not used">b</warning> = { _, _, y, _, _ -> y }
        def <warning descr="Assignment is not used">c</warning> = { _ -> 1 }
        def <warning descr="Assignment is not used">d</warning> = { (_) -> 1 }
    """.trimIndent())
  }

  @Test
  fun testVariableWithUnderscoreNameIsConsideredToBeUnused() {
    highlighting.doTestHighlighting("""
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
}