/*
 * Copyright (C) 2023 The Android Open Source Project
 * Modified 2025 by JetBrains s.r.o.
 * Copyright (C) 2025 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.shared.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.compose.ide.plugin.shared.util.QuickFixCheck
import com.intellij.compose.ide.plugin.shared.util.assertQuickFixNotAvailable
import com.intellij.compose.ide.plugin.shared.util.at
import com.intellij.compose.ide.plugin.shared.util.enableComposeInTest
import com.intellij.compose.ide.plugin.shared.util.invokeQuickFix
import com.intellij.compose.ide.plugin.shared.util.setUpCompilerArgumentsForComposeCompilerPlugin
import com.intellij.compose.ide.plugin.shared.util.unaryMinus
import com.intellij.compose.ide.plugin.shared.util.unaryPlus
import com.intellij.compose.ide.plugin.shared.util.with
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

  private fun testQuickFix(before: String, vararg checks: QuickFixCheck) {
    require(checks.isNotEmpty()) { "At least one QuickFixCheck is required" }

    myFixture.configureByText("Test.kt", before.trimIndent())
    checks.forEach { check ->
      when (check) {
        is QuickFixCheck.ExpectFix -> {
          myFixture.invokeQuickFix(check.caretAnchor, createComposableFixFilter(check.expectedFunctionName))
          myFixture.checkResult(check.after.trimIndent())
        }
        is QuickFixCheck.ExpectNoFix -> {
          check.anchors.forEach { myFixture.assertQuickFixNotAvailable(it, createComposableFixFilter(null)) }
        }
      }
    }
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
      """)
    runUndoTransparentWriteAction { file.virtualFile.isWritable = false }

    testQuickFix(
      before = """
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        fun NonComposableFunction() {
          MyFunctionWithLambda {
            ComposableFunction()  // invocation
          }
        }
      """,
      -"Composable|Function()  // invocation"
    )
  }

  fun `test missing composable invoke on function definition`() {
    testQuickFix(
      before = """
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        fun NonComposable<caret>Function() {
          ComposableFunction()
        }
      """,
      +"""
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        @Composable
        fun NonComposableFunction() {
          ComposableFunction()
        }
      """
    )
  }

  fun `test missing composable invoke on function call`() {
    testQuickFix(
      before = """
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        fun NonComposableFunction() {
          Composable<caret>Function()
        }
      """,
      +"""
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        @Composable
        fun NonComposableFunction() {
          ComposableFunction()
        }
      """
    )
  }

  fun `test missing composable without import`() {
    myFixture.addFileToProject("ComposableFunction.kt", """
      import androidx.compose.runtime.Composable
      @Composable
      fun ComposableFunction() {}
      """)

    testQuickFix(
      before = """
        fun NonComposable<caret>Function() {
          ComposableFunction()
        }
      """,
      +"""
        import androidx.compose.runtime.Composable

        @Composable
        fun NonComposableFunction() {
          ComposableFunction()
        }
      """
    )
  }

  fun `test errorInsideInlineLambda_invokeOnFunction`() {
    testQuickFix(
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
      """,
      +"""
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
      """ with "NonComposableFunction"
    )
  }

  fun `test error inside inline lambda invoke on function call`() {
    testQuickFix(
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
      """,
      +"""
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
    )
  }

  fun `test error inside non composable lambda`() {
    testQuickFix(
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
      """,
      +"""
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        fun functionThatTakesALambda(content: @Composable () -> Unit) {}
        fun NonComposableFunction() {
          functionThatTakesALambda {
            ComposableFunction()  // invocation
          }
        }
      """ at "Composable|Function()  // invocation",
      -"fun NonComposable|Function() {",
      -"functionThatTake|sALambda {",
      -"cont|ent",
      -"() -|> Unit"
    )
  }

  fun `test error inside non composable lambda param of anonymous function`() {
    testQuickFix(
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
      """,
      +"""
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        val functionTypedValThatTakesALambda = fun (content: @Composable () -> Unit) {}
        fun NonComposableFunction() {
          functionTypedValThatTakesALambda {
            ComposableFunction()  // invocation
          }
        }
      """ at "Composable|Function()  // invocation",
      -"fun NonComposable|Function() {",
      -"functionTypedValThatTake|sALambda {",
      -"cont|ent",
      -"() -|> Unit"
    )
  }

  fun `test error inside non composable positional lambda of anonymous function`() {
    testQuickFix(
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
      """,
      +"""
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        val FunctionTypedValWithTwoLambdas = fun (first: @Composable () -> Unit, second: () -> Unit) {}
        fun NonComposableFunction() {
          FunctionTypedValWithTwoLambdas({
            ComposableFunction()  // invocation
          }, {})
        }
      """ at "Composable|Function()  // invocation"
    )
  }

  fun `test error in returned lambda invoke on function call`() {
    testQuickFix(
      before = """
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        fun NonComposableFunction(): () -> Unit {
          return {
            ComposableFunction()  // invocation
          }
        }
      """,
      +"""
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        fun NonComposableFunction(): @Composable () -> Unit {
          return {
            ComposableFunction()  // invocation
          }
        }
      """ at "Composable|Function()  // invocation",
      -"fun NonComposable|Function()"
    )
  }

  fun `test error in returned lambda with explicit return type`() {
    testQuickFix(
      before = """
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        fun createComposable(): @Composable () -> Unit {
          val result: () -> Unit = {
            ComposableFunction()  // invocation
          }
          return result
        }
      """,
      +"""
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        fun createComposable(): @Composable () -> Unit {
          val result: @Composable () -> Unit = {
            ComposableFunction()  // invocation
          }
          return result
        }
      """ at "Composable|Function()  // invocation",
      -"fun createComposable|(): @Composable () -> Unit"
    )
  }

  fun `test error in returned lambda with parameters`() {
    testQuickFix(
      before = """
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction(text: String) {}
        fun createContentWithParam(): (String) -> Unit {
          return { text ->
            ComposableFunction(text)  // invocation
          }
        }
      """,
      +"""
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction(text: String) {}
        fun createContentWithParam(): @Composable (String) -> Unit {
          return { text ->
            ComposableFunction(text)  // invocation
          }
        }
      """ at "Composable|Function(text)  // invocation" with "createContentWithParam"
    )
  }

  fun `test error in returned lambda expression directly`() {
    testQuickFix(
      before = """
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        fun createContent(): () -> Unit = {
          ComposableFunction()  // invocation
        }
      """,
      +"""
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        fun createContent(): @Composable () -> Unit = {
          ComposableFunction()  // invocation
        }
      """ at "Composable|Function()  // invocation" with "createContent"
    )
  }

  fun `test error in returned lambda without explicit function return type`() {
    testQuickFix(
      before = """
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        fun createContent() = {
          ComposableFunction()  // invocation
        }
      """,
      +"""
        import androidx.compose.runtime.Composable
        @Composable
        fun ComposableFunction() {}
        fun createContent() = @androidx.compose.runtime.Composable {
          ComposableFunction()  // invocation
        }
      """ at "Composable|Function()  // invocation"
    )
  }

  fun `test error in class init`() {
    testQuickFix(
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
      """,
      -"fun getMy|Class(): Any",
      -"class My|Class",
      -"in|it",
      -"Composable|Function()  // invocation"
    )
  }

  fun `test error in property getter no setter invoke on function call`() {
    testQuickFix(
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
      """,
      +"""
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
      """ at "prop|erty" with "property.get()"
    )
  }

  fun `test errorInPropertyGetter_withSetter_invokeOnFunctionCall`() {
    testQuickFix(
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
      """,
      +"""
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
      """ at "Composable|Function()  // invocation" with "property.get()",
      -"var pro|perty: String"
    )
  }

  fun `test error in property setter no fixes`() {
    testQuickFix(
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
      """,
      -"fun getMy|Class(): Any",
      -"val prop|erty",
      -"se|t(value)",
      -"Composable|Function()  // invocation"
    )
  }

  fun `test error in property initializer invoke on function call`() {
    testQuickFix(
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
      """,
      +"""
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
      """ at "Composable|Function()  // invocation",
      -"fun getMy|Class(): Any",
      -"val prop|erty"
    )
  }

  fun `test error in property initializer with anonymous function invoke on function call`() {
    testQuickFix(
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
      """,
      +"""
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
      """ at "Composable|Function()  // invocation",
      -"fun getMy|Class(): Any"
    )
  }

  fun `test error in property initializer with anonymous function invoke on function`() {
    testQuickFix(
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
      """,
      +"""
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
      """ at "fun|()",
      -"fun getMy|Class(): Any"
    )
  }

  fun `test error in property initializer with anonymous function invoke on property`() {
    testQuickFix(
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
      """,
      +"""
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
      """ at "prop|erty",
      -"fun getMy|Class(): Any"
    )
  }

  fun `test error in property initializer with type invoke on function call`() {
    testQuickFix(
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
      """,
      +"""
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
      """ at "Composable|Function()  // invocation",
      -"fun getMy|Class(): Any",
      -"val prop|erty"
    )
  }
}
