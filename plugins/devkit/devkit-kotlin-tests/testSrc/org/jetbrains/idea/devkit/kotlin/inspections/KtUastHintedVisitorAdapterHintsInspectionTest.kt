// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import org.intellij.lang.annotations.Language
import org.jetbrains.idea.devkit.inspections.UastHintedVisitorAdapterHintsInspectionTestBase

class KtUastHintedVisitorAdapterHintsInspectionTest : UastHintedVisitorAdapterHintsInspectionTestBase() {

  fun `test missing hint`() {
    doTest(
      """
        internal class TestInspection : LocalInspectionTool() {
        
          override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              object : AbstractUastNonRecursiveVisitor() {
                override fun visitForExpression(node: UForExpression): Boolean {
                  inspectLoopExpression()
                  return true
                }
        
                override fun <warning descr="'visitDoWhileExpression' is unused because 'UDoWhileExpression' is not provided in the adapter hints">visitDoWhileExpression</warning>(node: UDoWhileExpression): Boolean {
                  inspectLoopExpression()
                  return true
                }
              },
              arrayOf(UForExpression::class.java)
            )
          }
          private fun inspectLoopExpression() {}
        }
      """.trimIndent())
  }

  fun `test missing hint when hints provided via field`() {
    doTest(
      """
        internal class TestInspection : LocalInspectionTool() {
          private val hints = arrayOf(UForExpression::class.java)
          override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              object : AbstractUastNonRecursiveVisitor() {
                override fun visitForExpression(node: UForExpression): Boolean {
                  inspectLoopExpression()
                  return true
                }
        
                override fun <warning descr="'visitDoWhileExpression' is unused because 'UDoWhileExpression' is not provided in the adapter hints">visitDoWhileExpression</warning>(node: UDoWhileExpression): Boolean {
                  inspectLoopExpression()
                  return true
                }
              },
              hints
            )
          }
          private fun inspectLoopExpression() {}
        }
      """.trimIndent())
  }

  fun `test missing hint when hints provided via variable`() {
    doTest(
      """
        internal class TestInspection : LocalInspectionTool() {
          override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
            val hints = arrayOf(UForExpression::class.java)
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              object : AbstractUastNonRecursiveVisitor() {
                override fun visitForExpression(node: UForExpression): Boolean {
                  inspectLoopExpression()
                  return true
                }
        
                override fun <warning descr="'visitDoWhileExpression' is unused because 'UDoWhileExpression' is not provided in the adapter hints">visitDoWhileExpression</warning>(node: UDoWhileExpression): Boolean {
                  inspectLoopExpression()
                  return true
                }
              },
              hints
            )
          }
          private fun inspectLoopExpression() {}
        }
      """.trimIndent())
  }

  fun `test missing hint when visitor is a non-anonymous class`() {
    doTest(
      """
        internal class TestInspection : LocalInspectionTool() {
        
          override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              MyVisitor(),
              arrayOf(UForExpression::class.java)
            )
          }
        
          private class MyVisitor : AbstractUastNonRecursiveVisitor() {
            override fun visitForExpression(node: UForExpression): Boolean {
              inspectLoopExpression()
              return true
            }
        
            override fun <warning descr="'visitDoWhileExpression' is unused because 'UDoWhileExpression' is not provided in the adapter hints">visitDoWhileExpression</warning>(node: UDoWhileExpression): Boolean {
              inspectLoopExpression()
              return true
            }
            private fun inspectLoopExpression() {}
          }
        }
      """.trimIndent())
  }

  fun `test missing hint when visitor class is provided via field`() {
    doTest(
      """
        internal class TestInspection : LocalInspectionTool() {
          private val VISITOR = object : AbstractUastNonRecursiveVisitor() {
              override fun visitForExpression(node: UForExpression): Boolean {
                inspectLoopExpression()
                return true
              }
        
              override fun <warning descr="'visitDoWhileExpression' is unused because 'UDoWhileExpression' is not provided in the adapter hints">visitDoWhileExpression</warning>(node: UDoWhileExpression): Boolean {
                inspectLoopExpression()
                return true
              }
            }
          override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              VISITOR,
              arrayOf(UForExpression::class.java)
            )
          }
          private fun inspectLoopExpression() {}
        }
      """.trimIndent())
  }

  fun `test redundant hint`() {
    doTest(
      """
        internal class TestInspection : LocalInspectionTool() {
        
          override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              object : AbstractUastNonRecursiveVisitor() {
                override fun visitForExpression(node: UForExpression): Boolean {
                  inspectLoopExpression()
                  return true
                }
              },
              arrayOf(UForExpression::class.java, <warning descr="'UDoWhileExpression' is provided in hints, but the element is not visited in the visitor">UDoWhileExpression::class</warning>.java)
            )
          }
          private fun inspectLoopExpression() {}
        }
      """.trimIndent())
  }

  fun `test redundant hint when hints provided via field`() {
    doTest(
      """
        internal class TestInspection : LocalInspectionTool() {
          private val hints = arrayOf(UForExpression::class.java, <warning descr="'UDoWhileExpression' is provided in hints, but the element is not visited in the visitor">UDoWhileExpression::class</warning>.java)
          override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              object : AbstractUastNonRecursiveVisitor() {
                override fun visitForExpression(node: UForExpression): Boolean {
                  inspectLoopExpression()
                  return true
                }
              },
              hints
            )
          }
          private fun inspectLoopExpression() {}
        }
      """.trimIndent())
  }

  fun `test redundant hint when hints provided via variable`() {
    doTest(
      """
        internal class TestInspection : LocalInspectionTool() {
          override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
            val hints = arrayOf(UForExpression::class.java, <warning descr="'UDoWhileExpression' is provided in hints, but the element is not visited in the visitor">UDoWhileExpression::class</warning>.java)
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              object : AbstractUastNonRecursiveVisitor() {
                override fun visitForExpression(node: UForExpression): Boolean {
                  inspectLoopExpression()
                  return true
                }
              },
              hints
            )
          }
          private fun inspectLoopExpression() {}
        }
      """.trimIndent())
  }

  fun `test redundant hint when visitor is a non-anonymous class`() {
    doTest(
      """
        internal class TestInspection : LocalInspectionTool() {
        
          override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              MyVisitor(),
              arrayOf(UForExpression::class.java, <warning descr="'UDoWhileExpression' is provided in hints, but the element is not visited in the visitor">UDoWhileExpression::class</warning>.java)
            )
          }
        }
        private class MyVisitor : AbstractUastNonRecursiveVisitor() {
          override fun visitForExpression(node: UForExpression): Boolean {
            inspectLoopExpression()
            return true
          }
          private fun inspectLoopExpression() {}
        }
      """.trimIndent())
  }

  fun `test redundant hint when visitor class is provided via variable`() {
    doTest(
      """
        internal class TestInspection : LocalInspectionTool() {
        
          override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
            val visitor = object : AbstractUastNonRecursiveVisitor() {
                override fun visitForExpression(node: UForExpression): Boolean {
                  inspectLoopExpression()
                  return true
                }
              }
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              visitor,
              arrayOf(UForExpression::class.java, <warning descr="'UDoWhileExpression' is provided in hints, but the element is not visited in the visitor">UDoWhileExpression::class</warning>.java)
            )
          }
          private fun inspectLoopExpression() {}
        }
      """.trimIndent())
  }

  fun `test should not report correct hints`() {
    doTest(
      """
        internal class TestInspection : LocalInspectionTool() {
        
          override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              object : AbstractUastNonRecursiveVisitor() {
                override fun visitForExpression(node: UForExpression): Boolean {
                  inspectLoopExpression()
                  return true
                }
        
                override fun visitForEachExpression(node: UForEachExpression): Boolean {
                  inspectLoopExpression()
                  return true
                }
              },
              arrayOf(UForExpression::class.java, UForEachExpression::class.java)
            )
          }
          private fun inspectLoopExpression() {}
        }
      """.trimIndent())
  }

  fun `test should not report missing hints when parent of visited classes provided`() {
    doTest(
      """
        internal class TestInspection : LocalInspectionTool() {
        
          override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              object : AbstractUastNonRecursiveVisitor() {
                override fun visitForExpression(node: UForExpression): Boolean {
                  inspectLoopExpression()
                  return true
                }
        
                override fun visitForEachExpression(node: UForEachExpression): Boolean {
                  inspectLoopExpression()
                  return true
                }
              },
              arrayOf(ULoopExpression::class.java)
            )
          }
          private fun inspectLoopExpression() {}
        }
      """.trimIndent())
  }

  fun `test should not report when UElement visited and any element hinted`() {
    doTest(
      """
        internal class TestInspection : LocalInspectionTool() {
        
          override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              object : AbstractUastNonRecursiveVisitor() {
                override fun visitElement(node: UElement): Boolean {
                  inspectLoopExpression()
                  return true
                }
              },
              arrayOf(UCallExpression::class.java)
            )
          }
          private fun inspectLoopExpression() {}
        }
      """.trimIndent())
  }

  fun `test should not report when ULiteralExpression or UPolyadicExpression visited and UInjectionHost element hinted`() {
    doTest(
      """
        class TestInspection : LocalInspectionTool() {
          @Override
          override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              object : AbstractUastNonRecursiveVisitor() {
                override fun visitPolyadicExpression(node: UPolyadicExpression): Boolean {
                  if (node !is UInjectionHost) return true
                  processInjectionHost(node)
                  return super.visitPolyadicExpression(node)
                }
                
                override fun visitLiteralExpression(node: ULiteralExpression): Boolean {
                  if (node !is UInjectionHost) return true
                  processInjectionHost(node)
                  return super.visitExpression(node)
                }
                private fun processInjectionHost(@Suppress("UNUSED_PARAMETER") node: UInjectionHost) {}
              },
              arrayOf(UInjectionHost::class.java)
            )
          }
        }
      """.trimIndent())
  }

  private fun doTest(@Language("kotlin") code: String) {
    myFixture.configureByText(
      "TestInspection.kt",
      //language=kotlin
      """
        import com.intellij.codeInspection.LocalInspectionTool
        import com.intellij.codeInspection.ProblemsHolder
        import com.intellij.lang.Language
        import com.intellij.psi.PsiElementVisitor
        import com.intellij.uast.UastHintedVisitorAdapter
        import org.jetbrains.uast.*
        import org.jetbrains.uast.expressions.*
        import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
        $code
      """.trimIndent())
    myFixture.testHighlighting("TestInspection.kt")
  }

}
