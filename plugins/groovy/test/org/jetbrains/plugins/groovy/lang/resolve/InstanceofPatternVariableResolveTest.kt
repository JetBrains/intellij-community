// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrPatternVariable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class InstanceofPatternVariableResolveTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1)
  }

  private val resolve by groovyResolveFixture(projectFixture)
  private val myFixture get() = resolve.fixture

  @BeforeEach
  fun setUp() {
    myFixture.addClass("""
      class A {}
    """.trimIndent())
    myFixture.addClass("""
      class B extends A {}
    """.trimIndent())
  }

  @Test
  fun testIfStatementBody() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          if (a instanceof B someName) {
            some<caret>Name
          }
        }
      }
    """.trimIndent(), GrPatternVariable::class.java)
  }

  @Test
  fun testIfStatementElseBody() {
    resolve.resolveByText<PsiElement>("""
      class X {
        def foo() {
          A a = new B()
          if (!(a instanceof B someName)) {}
          else {
            some<caret>Name
          }
        }
      }
    """.trimIndent(), null)
  }

  @Test
  fun testWhileStatementBody() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          while (a instanceof B someName) {
            some<caret>Name
          }
        }
      }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testForStatementBody() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          for (;a instanceof B someName;) {
            some<caret>Name
          }
        }
      }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testForStatementUpdate() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          for (;a instanceof B someName; some<caret>Name) {
          }
        }
      }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testConditionalExpressionBody() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          boolean b = a instanceof B someName ? some<caret>Name : null
        }
      }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testConditionalExpressionElseBody() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          boolean b = !(a instanceof B someName) ? null : some<caret>Name
        }
      }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testBinaryExpression() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          a instanceof B someName && some<caret>Name
        }
      }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testSwitchStatement() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          switch (a instanceof B someName) {
            case true:
              some<caret>Name
              break
            case false:
              break
          }
        }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testSwitchExpression() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          def x = switch (a instanceof B someName) {
            case true -> {
              some<caret>Name
              yield 1
              }
            case false -> {
              yield 2
              }
          }
        }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testContextualKeyword() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          if (a instanceof B var) {
            va<caret>r
          }
        }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testResolveAfterAndAnd() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          if (a instanceof B someName && some<caret>Name.toString().equals("foo")) {}
        }
      }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testResolveAfterExpressionAndAnd() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          if (a instanceof B someName && true && some<caret>Name.toString().equals("foo")) {}
        }
      }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testResolveAfterAndAndWithNegation() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          if (!(a instanceof B someName) && some<caret>Name.toString().equals("foo")) {}
        }
      }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testDoNotResolveAfterOrOr() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          if (a instanceof B someName || some<caret>Name.toString().equals("foo")) {}
        }
      }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testResolveAfterOrOrWithNegation() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          if (!(a instanceof B someName) || some<caret>Name.toString().equals("foo")) {}
        }
      }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testResolveAfterExpressionOrOrWithNegation() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          if (!(a instanceof B someName) || true || some<caret>Name.toString().equals("foo")) {}
        }
      }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testResolveIgnoresIfNegation() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          if (!(a instanceof B someName)) {
            some<caret>Name
          }
        }
      }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testResolveIgnoresConditionalNegation() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          boolean b = !(a instanceof B someName) ? some<caret>Name : null
        }
      }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testResolveIgnoresForUpdateNegation() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          for (;!(a instanceof B someName); some<caret>Name) {
          }
        }
      }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testResolveIgnoresForBodyNegation() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          for (;!(a instanceof B someName);) {
            some<caret>Name
          }
        }
      }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testResolveIgnoresWhileBodyNegation() {
    resolve.resolveByText("""
      class X {
        def foo() {
          A a = new B()
          while (!(a instanceof B someName)) {
            some<caret>Name
          }
        }
      }
    """, GrPatternVariable::class.java)
  }

  @Test
  fun testDoNotResolvePrecedingDefinitionIfStatement() {
    resolve.resolveByText<PsiElement>("""
      class X {
        def foo() {
          A a = new B()
          if (!(some<caret>Name || a instanceof C someName)) {
          }
        }
      }
    """, null)
  }

  @Test
  fun testDoNotResolvePrecedingDefinitionConditionalExpression() {
    resolve.resolveByText<PsiElement>("""
      class X {
        def foo() {
          A a = new B()
          boolean b = some<caret>Name || !(a instanceof C someName) ? true : false {
          }
        }
      }
    """, null)
  }

  @Test
  fun testResolveAfterImplicationExpression() {
    resolve.resolveByText("""
      class Main {
          static class A {}
          static class B extends A{}

          static void main(String[] args) {
              A a = new B()
              def z = a instanceof B variable ==> variab<caret>le
          }
      }
    """.trimIndent(), GrPatternVariable::class.java)
  }

  @Test
  fun testResolveAfterImplicationExpressionNegation() {
    resolve.resolveByText("""
      class Main {
          static class A {}
          static class B extends A{}

          static void main(String[] args) {
              A a = new B()
              def z = !(a instanceof B variable) ==> variab<caret>le
          }
      }
    """.trimIndent(), GrPatternVariable::class.java)
  }
}