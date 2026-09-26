// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import org.intellij.lang.annotations.Language

class UastHintedVisitorAdapterHintsInspectionTest : UastHintedVisitorAdapterHintsInspectionTestBase() {

  fun `test missing hint`() {
    doTest(
      """
        class TestInspection extends LocalInspectionTool {
          @Override
          public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              new AbstractUastNonRecursiveVisitor() {
                @Override
                public boolean visitForExpression(UForExpression node) {
                  inspectLoopExpression();
                  return true;
                }
                @Override
                public boolean <warning descr="'visitDoWhileExpression' is unused because 'UDoWhileExpression' is not provided in the adapter hints">visitDoWhileExpression</warning>(UDoWhileExpression node) {
                  inspectLoopExpression();
                  return true;
                }
                private void inspectLoopExpression() {}
              },
              new Class[]{UForExpression.class}
            );
          }
        }
      """.trimIndent())
  }

  fun `test missing hint when hints provided via field`() {
    doTest(
      """
        class TestInspection extends LocalInspectionTool {
          @SuppressWarnings("unchecked")
          private static final Class<? extends UElement>[] HINTS = new Class[]{UForExpression.class};
          @Override
          public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              new AbstractUastNonRecursiveVisitor() {
                @Override
                public boolean visitForExpression(UForExpression node) {
                  inspectLoopExpression();
                  return true;
                }
                @Override
                public boolean <warning descr="'visitDoWhileExpression' is unused because 'UDoWhileExpression' is not provided in the adapter hints">visitDoWhileExpression</warning>(UDoWhileExpression node) {
                  inspectLoopExpression();
                  return true;
                }
                private void inspectLoopExpression() {}
              },
              HINTS
            );
          }
        }
      """.trimIndent())
  }

  fun `test missing hint when hints provided via variable`() {
    doTest(
      """
        class TestInspection extends LocalInspectionTool {
          @SuppressWarnings("unchecked")
          @Override
          public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
            Class<? extends UElement>[] hints = new Class[]{UForExpression.class};
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              new AbstractUastNonRecursiveVisitor() {
                @Override
                public boolean visitForExpression(UForExpression node) {
                  inspectLoopExpression();
                  return true;
                }
                @Override
                public boolean <warning descr="'visitDoWhileExpression' is unused because 'UDoWhileExpression' is not provided in the adapter hints">visitDoWhileExpression</warning>(UDoWhileExpression node) {
                  inspectLoopExpression();
                  return true;
                }
                private void inspectLoopExpression() {}
              },
              hints
            );
          }
        }
      """.trimIndent())
  }

  fun `test missing hint when visitor is a non-anonymous class`() {
    doTest(
      """
        class TestInspection extends LocalInspectionTool {
          @SuppressWarnings("unchecked")
          @Override
          public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
            return UastHintedVisitorAdapter.create(Language.ANY, new MyVisitor(), new Class[]{UForExpression.class});
          }
          private static class MyVisitor extends AbstractUastNonRecursiveVisitor {
            @Override
            public boolean visitForExpression(UForExpression node) {
              inspectLoopExpression();
              return true;
            }
            @Override
            public boolean <warning descr="'visitDoWhileExpression' is unused because 'UDoWhileExpression' is not provided in the adapter hints">visitDoWhileExpression</warning>(UDoWhileExpression node) {
              inspectLoopExpression();
              return true;
            }
            private void inspectLoopExpression() {}
          }
        }
      """.trimIndent())
  }

  fun `test missing hint when visitor class is provided via field`() {
    doTest(
      """
        class TestInspection extends LocalInspectionTool {
          private static final AbstractUastNonRecursiveVisitor VISITOR = new AbstractUastNonRecursiveVisitor() {
              @Override
              public boolean visitForExpression(UForExpression node) {
                inspectLoopExpression();
                return true;
              }
              @Override
              public boolean <warning descr="'visitDoWhileExpression' is unused because 'UDoWhileExpression' is not provided in the adapter hints">visitDoWhileExpression</warning>(UDoWhileExpression node) {
                inspectLoopExpression();
                return true;
              }
              private void inspectLoopExpression() {}
            };
          @Override
          public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              VISITOR,
              new Class[]{UForExpression.class}
            );
          }
        }
      """.trimIndent())
  }

  fun `test redundant hint`() {
    doTest(
      """
        class TestInspection extends LocalInspectionTool {
          @Override
          public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              new AbstractUastNonRecursiveVisitor() {
                @Override
                public boolean visitForExpression(UForExpression node) {
                  inspectLoopExpression();
                  return true;
                }
                private void inspectLoopExpression() {}
              },
              new Class[]{UForExpression.class, <warning descr="'UDoWhileExpression' is provided in hints, but the element is not visited in the visitor">UDoWhileExpression.class</warning>}
            );
          }
        }
      """.trimIndent())
  }

  fun `test redundant hint when hints provided via field`() {
    doTest(
      """
        class TestInspection extends LocalInspectionTool {
          @SuppressWarnings("unchecked")
          private static final Class<? extends UElement>[] HINTS = new Class[]{UForExpression.class, <warning descr="'UDoWhileExpression' is provided in hints, but the element is not visited in the visitor">UDoWhileExpression.class</warning>};
          @Override
          public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              new AbstractUastNonRecursiveVisitor() {
                @Override
                public boolean visitForExpression(UForExpression node) {
                  inspectLoopExpression();
                  return true;
                }
                private void inspectLoopExpression() {}
              },
              HINTS
            );
          }
        }
      """.trimIndent())
  }

  fun `test redundant hint when hints provided via variable`() {
    doTest(
      """
        class TestInspection extends LocalInspectionTool {
          @SuppressWarnings("unchecked")
          @Override
          public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
            Class<? extends UElement>[] hints = new Class[]{UForExpression.class, <warning descr="'UDoWhileExpression' is provided in hints, but the element is not visited in the visitor">UDoWhileExpression.class</warning>};
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              new AbstractUastNonRecursiveVisitor() {
                @Override
                public boolean visitForExpression(UForExpression node) {
                  inspectLoopExpression();
                  return true;
                }
                private void inspectLoopExpression() {}
              },
              hints
            );
          }
        }
      """.trimIndent())
  }

  fun `test redundant hint when visitor is a non-anonymous class`() {
    doTest(
      """
        class TestInspection extends LocalInspectionTool {
          @Override
          public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              new MyVisitor(),
              new Class[]{UForExpression.class, <warning descr="'UDoWhileExpression' is provided in hints, but the element is not visited in the visitor">UDoWhileExpression.class</warning>}
            );
          }
          private static class MyVisitor extends AbstractUastNonRecursiveVisitor {
            @Override
            public boolean visitForExpression(UForExpression node) {
              inspectLoopExpression();
              return true;
            }
            private void inspectLoopExpression() {}
          }
        }
      """.trimIndent())
  }

  fun `test redundant hint when visitor class is provided via variable`() {
    doTest(
      """
        class TestInspection extends LocalInspectionTool {
          @Override
          public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
            AbstractUastNonRecursiveVisitor visitor = new AbstractUastNonRecursiveVisitor() {
                @Override
                public boolean visitForExpression(UForExpression node) {
                  inspectLoopExpression();
                  return true;
                }
                private void inspectLoopExpression() {}
              };
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              visitor,
              new Class[]{UForExpression.class, <warning descr="'UDoWhileExpression' is provided in hints, but the element is not visited in the visitor">UDoWhileExpression.class</warning>}
            );
          }
        }
      """.trimIndent())
  }

  fun `test should not report correct hints`() {
    doTest(
      """
        class TestInspection extends LocalInspectionTool {
          @Override
          public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              new AbstractUastNonRecursiveVisitor() {
                @Override
                public boolean visitForExpression(UForExpression node) {
                  inspectLoopExpression();
                  return true;
                }
                @Override
                public boolean visitForEachExpression(UForEachExpression node) {
                  inspectLoopExpression();
                  return true;
                }
                private void inspectLoopExpression() {}
              },
              new Class[]{UForExpression.class, UForEachExpression.class}
            );
          }
        }
      """.trimIndent())
  }

  fun `test should not report missing hints when parent of visited classes provided`() {
    doTest(
      """
        class TestInspection extends LocalInspectionTool {
          @Override
          public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              new AbstractUastNonRecursiveVisitor() {
                @Override public boolean visitForExpression(UForExpression node) {
                  inspectLoopExpression();
                  return true;
                }
                @Override public boolean visitForEachExpression(UForEachExpression node) {
                  inspectLoopExpression();
                  return true;
                }
                private void inspectLoopExpression() {}
              },
              new Class[]{ULoopExpression.class}
            );
          }
        }
      """.trimIndent())
  }

  fun `test should not report when UElement visited and any element hinted`() {
    doTest(
      """
        class TestInspection extends LocalInspectionTool {
          @Override
          public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              new AbstractUastNonRecursiveVisitor() {
                @Override
                public boolean visitElement(UElement node) {
                  inspectLoopExpression();
                  return true;
                }
                private void inspectLoopExpression() {}
              },
              new Class[]{UCallExpression.class}
            );
          }
        }
      """.trimIndent())
  }

  fun `test should not report when ULiteralExpression or UPolyadicExpression visited and UInjectionHost element hinted`() {
    doTest(
      """
        class TestInspection extends LocalInspectionTool {
          @Override
          public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
            return UastHintedVisitorAdapter.create(
              Language.ANY,
              new AbstractUastNonRecursiveVisitor() {
                @Override
                public boolean visitPolyadicExpression(UPolyadicExpression node) {
                  if (!(node instanceof UInjectionHost)) return true;
                  processInjectionHost((UInjectionHost)node);
                  return super.visitPolyadicExpression(node);
                }
                
                @Override
                public boolean visitLiteralExpression(ULiteralExpression node) {
                  if (!(node instanceof UInjectionHost)) return true;
                  processInjectionHost((UInjectionHost)node);
                  return super.visitExpression(node);
                }
                private void processInjectionHost(UInjectionHost node) {}
              },
              new Class[]{UInjectionHost.class}
            );
          }
        }
      """.trimIndent())
  }

  private fun doTest(@Language("JAVA") code: String) {
    myFixture.configureByText(
      "TestInspection.java",
      //language=JAVA
      """
        import com.intellij.codeInspection.LocalInspectionTool;
        import com.intellij.codeInspection.ProblemsHolder;
        import com.intellij.lang.Language;
        import com.intellij.psi.PsiElementVisitor;
        import com.intellij.uast.UastHintedVisitorAdapter;
        import org.jetbrains.uast.*;
        import org.jetbrains.uast.expressions.*;
        import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;
        $code
      """.trimIndent())
    myFixture.testHighlighting("TestInspection.java")
  }

}
