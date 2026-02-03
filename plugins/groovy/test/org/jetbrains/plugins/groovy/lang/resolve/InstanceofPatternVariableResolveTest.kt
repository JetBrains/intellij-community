// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrPatternVariable

class InstanceofPatternVariableResolveTest : GroovyResolveTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
      class A {}
    """.trimIndent())
    myFixture.addClass("""
      class B extends A {}
    """.trimIndent())
  }

  fun testIfStatementBody() {
    resolveByText("""
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

  fun testIfStatementElseBody() {
    resolveByText<PsiElement>("""
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

  fun testWhileStatementBody() {
    resolveByText("""
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

  fun testForStatementBody() {
    resolveByText("""
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

  fun testForStatementUpdate() {
    resolveByText("""
      class X {
        def foo() {
          A a = new B()
          for (;a instanceof B someName; some<caret>Name) {
          }
        }
      }
    """, GrPatternVariable::class.java)
  }

  fun testConditionalExpressionBody() {
    resolveByText("""
      class X {
        def foo() {
          A a = new B()
          boolean b = a instanceof B someName ? some<caret>Name : null
        }
      }
    """, GrPatternVariable::class.java)
  }


  fun testConditionalExpressionElseBody() {
    resolveByText("""
      class X {
        def foo() {
          A a = new B()
          boolean b = !(a instanceof B someName) ? null : some<caret>Name
        }
      }
    """, GrPatternVariable::class.java)
  }

  fun testBinaryExpression() {
    resolveByText("""
      class X {
        def foo() {
          A a = new B()
          a instanceof B someName && some<caret>Name
        }
      }
    """, GrPatternVariable::class.java)
  }

  fun testSwitchStatement() {
    resolveByText("""
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

  fun testSwitchExpression() {
    resolveByText("""
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

  fun testContextualKeyword() {
    resolveByText("""
      class X {
        def foo() {
          A a = new B()
          if (a instanceof B var) {
            va<caret>r
          }
        }
    """, GrPatternVariable::class.java)
  }

  fun testResolveAfterAndAnd() {
    resolveByText("""
      class X {
        def foo() {
          A a = new B()
          if (a instanceof B someName && some<caret>Name.toString().equals("foo")) {}
        }
      }
    """, GrPatternVariable::class.java)
  }

  fun testResolveAfterExpressionAndAnd() {
    resolveByText("""
      class X {
        def foo() {
          A a = new B()
          if (a instanceof B someName && true && some<caret>Name.toString().equals("foo")) {}
        }
      }
    """, GrPatternVariable::class.java)
  }

  fun testResolveAfterAndAndWithNegation() {
    resolveByText("""
      class X {
        def foo() {
          A a = new B()
          if (!(a instanceof B someName) && some<caret>Name.toString().equals("foo")) {}
        }
      }
    """, GrPatternVariable::class.java)
  }

  fun testDoNotResolveAfterOrOr() {
    resolveByText("""
      class X {
        def foo() {
          A a = new B()
          if (a instanceof B someName || some<caret>Name.toString().equals("foo")) {}
        }
      }
    """, GrPatternVariable::class.java)
  }

  fun testResolveAfterOrOrWithNegation() {
    resolveByText("""
      class X {
        def foo() {
          A a = new B()
          if (!(a instanceof B someName) || some<caret>Name.toString().equals("foo")) {}
        }
      }
    """, GrPatternVariable::class.java)
  }

  fun testResolveAfterExpressionOrOrWithNegation() {
    resolveByText("""
      class X {
        def foo() {
          A a = new B()
          if (!(a instanceof B someName) || true || some<caret>Name.toString().equals("foo")) {}
        }
      }
    """, GrPatternVariable::class.java)
  }

  fun testResolveIgnoresIfNegation() {
    resolveByText("""
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

  fun testResolveIgnoresConditionalNegation() {
    resolveByText("""
      class X {
        def foo() {
          A a = new B()
          boolean b = !(a instanceof B someName) ? some<caret>Name : null
        }
      }
    """, GrPatternVariable::class.java)
  }

  fun testResolveIgnoresForUpdateNegation() {
    resolveByText("""
      class X {
        def foo() {
          A a = new B()
          for (;!(a instanceof B someName); some<caret>Name) {
          }
        }
      }
    """, GrPatternVariable::class.java)
  }

  fun testResolveIgnoresForBodyNegation() {
    resolveByText("""
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


  fun testResolveIgnoresWhileBodyNegation() {
    resolveByText("""
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

  fun testDoNotResolvePrecedingDefinitionIfStatement() {
    resolveByText<PsiElement>("""
      class X {
        def foo() {
          A a = new B()
          if (!(some<caret>Name || a instanceof C someName)) {
          }
        }
      }
    """, null)
  }

  fun testDoNotResolvePrecedingDefinitionConditionalExpression() {
    resolveByText<PsiElement>("""
      class X {
        def foo() {
          A a = new B()
          boolean b = some<caret>Name || !(a instanceof C someName) ? true : false {
          }
        }
      }
    """, null)
  }

  fun testResolveAfterImplicationExpression() {
    resolveByText("""
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

  fun testResolveAfterImplicationExpressionNegation() {
    resolveByText("""
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