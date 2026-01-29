// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.shared.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.compose.ide.plugin.shared.util.QuickFixTestBuilder
import com.intellij.compose.ide.plugin.shared.util.enableComposeInTest
import com.intellij.compose.ide.plugin.shared.util.setUpCompilerArgumentsForComposeCompilerPlugin
import com.intellij.compose.ide.plugin.shared.util.testQuickFix
import com.intellij.openapi.application.runUndoTransparentWriteAction
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

abstract class ComposableAnnotationQuickFixTest : KotlinLightCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

  override fun setUp() {
    super.setUp()
    myFixture.enableComposeInTest()
    setUpCompilerArgumentsForComposeCompilerPlugin(myFixture.project)
  }

  private fun testQuickFix(init: QuickFixTestBuilder.() -> Unit) {
    testQuickFix(fixture = myFixture, fixFilterFactory = ::createComposableFixFilter, init = init)
  }

  private fun createComposableFixFilter(expectedFunctionName: String?): (IntentionAction) -> Boolean = { action ->
    if (expectedFunctionName != null) {
      action.text == ComposeIdeBundle.message("compose.add.composable.to.element.name", expectedFunctionName)
    }
    else {
      action.text.startsWith("Add '@Composable' to ")
    }
  }

  fun `test missing composable read only file`() {
    val file = myFixture.addFileToProject("MyFunctionWithLambda.kt", """
      fun MyFunctionWithLambda(content: () -> Unit) { 
        content()
      }
      """.trimIndent())
    runUndoTransparentWriteAction { file.virtualFile.isWritable = false }

    testQuickFix {
      before = """
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        fun NonComposableFunction() {
          MyFunctionWithLambda {
            ComposableFunction()  // invocation
          }
        }
      """
      expectUnavailableFix {
        at("Composable|Function()  // invocation")
      }
    }
  }

  fun `test missing composable invoke on function definition`() {
    testQuickFix {
      before = """
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        fun NonComposable<caret>Function() {
          ComposableFunction()
        }
      """
      expectFix {
        after = """
          import androidx.compose.runtime.Composable
          @Composable
          fun ComposableFunction() {}
          @Composable
          fun NonComposableFunction() {
            ComposableFunction()
          }
        """
      }
    }
  }

  fun `test missing composable invoke on function call`() {
    testQuickFix {
      before = """
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        fun NonComposableFunction() {
          Composable<caret>Function()
        }
      """
      expectFix {
        after = """
          import androidx.compose.runtime.Composable
          @Composable
          fun ComposableFunction() {}
          @Composable
          fun NonComposableFunction() {
            ComposableFunction()
          }
        """
      }
    }
  }

  fun `test missing composable without import`() {
    myFixture.addFileToProject("ComposableFunction.kt", """
      import androidx.compose.runtime.Composable
      @Composable
      fun ComposableFunction() {}
      """.trimIndent())

    testQuickFix {
      before = """
        fun NonComposable<caret>Function() {
          ComposableFunction()
        }
      """
      expectFix {
        after = """
          import androidx.compose.runtime.Composable
  
          @Composable
          fun NonComposableFunction() {
            ComposableFunction()
          }
        """
      }
    }
  }

  fun `test errorInsideInlineLambda_invokeOnFunction`() {
    testQuickFix {
      before = """
        import androidx.compose.runtime.Composable
        // Redefine a version of `let` since the real one isn't defined in the test context.
        inline fun <T, R> T.myLet(block: (T) -> R): R = block(this)
        @Composable
        fun ComposableFunction() {}
        fun NonComposable<caret>Function() {
          val foo = 1
          foo.myLet {
            ComposableFunction()
          }
        }
      """
      expectFix {
        after = """
          import androidx.compose.runtime.Composable
          // Redefine a version of `let` since the real one isn't defined in the test context.
          inline fun <T, R> T.myLet(block: (T) -> R): R = block(this)
          @Composable
          fun ComposableFunction() {}
          @Composable
          fun NonComposableFunction() {
            val foo = 1
            foo.myLet {
              ComposableFunction()
            }
          }
        """
        expectedFunctionName = "NonComposableFunction"
      }
    }
  }

  fun `test error inside inline lambda invoke on function call`() {
    testQuickFix {
      before = """
        import androidx.compose.runtime.Composable
        // Redefine a version of `let` since the real one isn't defined in the test context.
        inline fun <T, R> T.myLet(block: (T) -> R): R = block(this)
        @Composable
        fun ComposableFunction() {}
        fun NonComposableFunction() {
          val foo = 1
          foo.myLet {
            Composable<caret>Function()
          }
        }
      """
      expectFix {
        after = """
          import androidx.compose.runtime.Composable
          // Redefine a version of `let` since the real one isn't defined in the test context.
          inline fun <T, R> T.myLet(block: (T) -> R): R = block(this)
          @Composable
          fun ComposableFunction() {}
          @Composable
          fun NonComposableFunction() {
            val foo = 1
            foo.myLet {
              ComposableFunction()
            }
          }
        """
      }
    }
  }

  fun `test error inside non composable lambda`() {
    testQuickFix {
      before = """
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        fun functionThatTakesALambda(content: () -> Unit) {}
        fun NonComposableFunction() {
          functionThatTakesALambda {
            ComposableFunction()  // invocation
          }
        }
      """
      expectFix {
        after = """
          import androidx.compose.runtime.Composable
          @Composable
          fun ComposableFunction() {}
          fun functionThatTakesALambda(content: @Composable () -> Unit) {}
          fun NonComposableFunction() {
            functionThatTakesALambda {
              ComposableFunction()  // invocation
            }
          }
        """
        caretAnchor = "Composable|Function()  // invocation"
      }
      expectUnavailableFix {
        at("fun NonComposable|Function() {", "functionThatTake|sALambda {", "cont|ent", "() -|> Unit")
      }
    }
  }

  fun `test error inside non composable lambda param of anonymous function`() {
    testQuickFix {
      before = """
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        val functionTypedValThatTakesALambda = fun (content: () -> Unit) {}
        fun NonComposableFunction() {
          functionTypedValThatTakesALambda {
            ComposableFunction()  // invocation
          }
        }
        """
      expectFix {
        after = """
          import androidx.compose.runtime.Composable
          @Composable
          fun ComposableFunction() {}
          val functionTypedValThatTakesALambda = fun (content: @Composable () -> Unit) {}
          fun NonComposableFunction() {
            functionTypedValThatTakesALambda {
              ComposableFunction()  // invocation
            }
          }
        """
        caretAnchor = "Composable|Function()  // invocation"
      }
      expectUnavailableFix {
        at("fun NonComposable|Function() {", "functionTypedValThatTake|sALambda {", "cont|ent", "() -|> Unit")
      }
    }
  }

  fun `test error inside non composable positional lambda of anonymous function`() {
    testQuickFix {
      before = """
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        val FunctionTypedValWithTwoLambdas = fun (first: () -> Unit, second: () -> Unit) {}
        fun NonComposableFunction() {
          FunctionTypedValWithTwoLambdas({
            ComposableFunction()  // invocation
          }, {})
        }
      """
      expectFix {
        after = """
          import androidx.compose.runtime.Composable
          @Composable
          fun ComposableFunction() {}
          val FunctionTypedValWithTwoLambdas = fun (first: @Composable () -> Unit, second: () -> Unit) {}
          fun NonComposableFunction() {
            FunctionTypedValWithTwoLambdas({
              ComposableFunction()  // invocation
            }, {})
          }
        """
        caretAnchor = "Composable|Function()  // invocation"
      }
    }
  }

  fun `test error in class init`() {
    testQuickFix {
      before = """
        package com.example
        import androidx.compose.runtime.Composable
  
        @Composable
        fun ComposableFunction() {}
  
        fun getMyClass(): Any {
          class MyClass {
            init {
              ComposableFunction()  // invocation
            }
          }
          return MyClass()
        }
      """
      expectUnavailableFix {
        at("fun getMy|Class(): Any", "class My|Class", "in|it", "Composable|Function()  // invocation")
      }
    }
  }

  fun `test error in property getter no setter invoke on function call`() {
    testQuickFix {
      before = """
        package com.example
        import androidx.compose.runtime.Composable
  
        @Composable
        fun ComposableFunction() {}
  
        fun getMyClass(): Any {
          class MyClass {
            val property: String
              get() {
                ComposableFunction()  // invocation
                return ""
              }
          }
          return MyClass()
        }
      """
      expectFix {
        after = """
          package com.example
          import androidx.compose.runtime.Composable
    
          @Composable
          fun ComposableFunction() {}
    
          fun getMyClass(): Any {
            class MyClass {
              val property: String
                @Composable
                get() {
                  ComposableFunction()  // invocation
                  return ""
                }
            }
            return MyClass()
          }
        """
        caretAnchor = "prop|erty"
        expectedFunctionName = "property.get()"
      }
    }
  }

  fun `test errorInPropertyGetter_withSetter_invokeOnFunctionCall`() {
    testQuickFix {
      before = """
        package com.example
        import androidx.compose.runtime.Composable
  
        @Composable
        fun ComposableFunction() {}
  
        fun getMyClass(): Any {
          class MyClass {
            var property: String = "foo"
              get() {
                ComposableFunction()  // invocation
                return ""
              }
              set(newValue) {
                field = newValue + "bar"
              }
          }
          return MyClass()
        }
      """
      expectFix {
        after = """
          package com.example
          import androidx.compose.runtime.Composable
    
          @Composable
          fun ComposableFunction() {}
    
          fun getMyClass(): Any {
            class MyClass {
              var property: String = "foo"
                @Composable
                get() {
                  ComposableFunction()  // invocation
                  return ""
                }
                set(newValue) {
                  field = newValue + "bar"
                }
            }
            return MyClass()
          }
        """
        caretAnchor = "Composable|Function()  // invocation"
        expectedFunctionName = "property.get()"
      }
      expectUnavailableFix {
        at("var pro|perty: String")
      }
    }
  }

  fun `test error in property setter no fixes`() {
    testQuickFix {
      before = """
        package com.example
        import androidx.compose.runtime.Composable
        @Composable fun ComposableFunction() {}
        fun getMyClass(): Any {
          class MyClass {
            val property: String
              get() {
                return ""
              }
              set(value) {
                Composable<caret>Function()  // invocation
              }
          }
          return MyClass()
        }
      """
      expectUnavailableFix {
        at("fun getMy|Class(): Any", "val prop|erty", "se|t(value)", "Composable|Function()  // invocation")
      }
    }
  }

  fun `test error in property initializer invoke on function call`() {
    testQuickFix {
      before = """
        import androidx.compose.runtime.Composable
        @Composable fun ComposableFunction() {}
        fun getMyClass(): Any {
          class MyClass {
            val property = {
              ComposableFunction()  // invocation
              ""
            }
          }
          return MyClass()
        }
        """
      expectFix {
        after = """
          import androidx.compose.runtime.Composable
          @Composable fun ComposableFunction() {}
          fun getMyClass(): Any {
            class MyClass {
              val property = @androidx.compose.runtime.Composable {
                ComposableFunction()  // invocation
                ""
              }
            }
            return MyClass()
          }
        """
        caretAnchor = "Composable|Function()  // invocation"
      }
      expectUnavailableFix {
        at("fun getMy|Class(): Any", "val prop|erty")
      }
    }
  }

  fun `test error in property initializer with anonymous function invoke on function call`() {
    testQuickFix {
      before = """
        import androidx.compose.runtime.Composable
        @Composable fun ComposableFunction() {}
        fun getMyClass(): Any {
          class MyClass {
            val property = fun() {
              ComposableFunction()  // invocation
            }
          }
          return MyClass()
        }
        """
      expectFix {
        after = """
          import androidx.compose.runtime.Composable
          @Composable fun ComposableFunction() {}
          fun getMyClass(): Any {
            class MyClass {
              val property = @Composable
              fun() {
                ComposableFunction()  // invocation
              }
            }
            return MyClass()
          }
        """
        caretAnchor = "Composable|Function()  // invocation"
      }
      expectUnavailableFix {
        at("fun getMy|Class(): Any")
      }
    }
  }

  fun `test error in property initializer with anonymous function invoke on function`() {
    testQuickFix {
      before = """
        import androidx.compose.runtime.Composable
        @Composable fun ComposableFunction() {}
        fun getMyClass(): Any {
          class MyClass {
            val property = fun() {
              ComposableFunction()  // invocation
            }
          }
          return MyClass()
        }
        """
      expectFix {
        after = """
          import androidx.compose.runtime.Composable
          @Composable fun ComposableFunction() {}
          fun getMyClass(): Any {
            class MyClass {
              val property = @Composable
              fun() {
                ComposableFunction()  // invocation
              }
            }
            return MyClass()
          }
        """
        caretAnchor = "fun|()"
      }
      expectUnavailableFix {
        at("fun getMy|Class(): Any")
      }
    }
  }

  fun `test error in property initializer with anonymous function invoke on property`() {
    testQuickFix {
      before = """
        import androidx.compose.runtime.Composable
        @Composable fun ComposableFunction() {}
        fun getMyClass(): Any {
          class MyClass {
            val property = fun() {
              ComposableFunction()  // invocation
            }
          }
          return MyClass()
        }
        """
      expectFix {
        after = """
          import androidx.compose.runtime.Composable
          @Composable fun ComposableFunction() {}
          fun getMyClass(): Any {
            class MyClass {
              val property = @Composable
              fun() {
                ComposableFunction()  // invocation
              }
            }
            return MyClass()
          }
        """
        caretAnchor = "prop|erty"
      }
      expectUnavailableFix {
        at("fun getMy|Class(): Any")
      }
    }
  }

  fun `test error in property initializer with type invoke on function call`() {
    testQuickFix {
      before = """
        import androidx.compose.runtime.Composable
        @Composable fun ComposableFunction() {}
        fun getMyClass(): Any {
          class MyClass {
            val property: () -> String = {
              ComposableFunction()  // invocation
              ""
            }
          }
          return MyClass()
        }
        """
      expectFix {
        after = """
          import androidx.compose.runtime.Composable
          @Composable fun ComposableFunction() {}
          fun getMyClass(): Any {
            class MyClass {
              val property: @Composable () -> String = {
                ComposableFunction()  // invocation
                ""
              }
            }
            return MyClass()
          }
        """
        caretAnchor = "Composable|Function()  // invocation"
      }
      expectUnavailableFix {
        at("fun getMy|Class(): Any", "val prop|erty")
      }
    }
  }
}
