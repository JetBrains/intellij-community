/*
 * Copyright (C) 2023 The Android Open Source Project
 * Modified 2026 by JetBrains s.r.o.
 * Copyright (C) 2026 JetBrains s.r.o.
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
package com.intellij.compose.ide.plugin.k2.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

/**
 * Tests for adding `@Composable` annotation quick fix
 *
 * Based on: [com.android.tools.compose.intentions.AddComposableAnnotationQuickFixTest]
 */
class K2AddComposableAnnotationQuickFixTest : KotlinLightCodeInsightFixtureTestCase() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
  override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

  override fun setUp() {
    super.setUp()
    myFixture.stubComposableAnnotation()
    setUpCompilerArgumentsForComposeCompilerPlugin(myFixture.project)
  }

  fun `test missing composable read only file`() {
    addExtraFile("MyFunctionWithLambda.kt",
                 """
                   fun MyFunctionWithLambda(content: () -> Unit) {
                   content()
                 }
                 """,
                 { virtualFile.isWritable = false }
    )

    testQuickFix(
      before = """
          import androidx.compose.runtime.Composable
          @Composable
          fun ComposableFunction() {}
          fun NonComposableFunction() {
            MyFunctionWithLambda {
              Composable<caret>Function()
            }
          }
      """
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
      after = """
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
      after = """
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
    addExtraFile("ComposableFunction.kt",
                 """
                    import androidx.compose.runtime.Composable
                    @Composable
                    fun ComposableFunction() {}
                 """
    )


    testQuickFix(
      before = """
          fun NonComposable<caret>Function() {
              ComposableFunction()
          }
      """,
      after = """
          import androidx.compose.runtime.Composable

          @Composable
          fun NonComposableFunction() {
              ComposableFunction()
          }
      """.trimIndent()
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
      """,
      unavailableFixAt = listOf("fun NonComposable|Function() {", "functionThatTake|sALambda {", "cont|ent", "() -|> Unit"),
      caretAnchor = "Composable|Function()  // invocation"
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
        """,
      unavailableFixAt = listOf("fun NonComposable|Function() {", "functionTypedValThatTake|sALambda {", "cont|ent", "() -|> Unit"),
      caretAnchor = "Composable|Function()  // invocation"
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
      """.trimIndent(),
      unavailableFixAt = listOf("fun getMy|Class(): Any", "class My|Class", "in|it", "Composable|Function()  // invocation"),
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
      """,
      caretAnchor = "prop|erty",
      expectedFunctionName = "property.get()"
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
      """,
      unavailableFixAt = listOf("var pro|perty: String"),
      caretAnchor = "Composable|Function()  // invocation",
      expectedFunctionName = "property.get()"
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
      unavailableFixAt = listOf("fun getMy|Class(): Any", "val prop|erty", "se|t(value)", "Composable|Function()  // invocation"),
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
        """,
      unavailableFixAt = listOf("fun getMy|Class(): Any", "val prop|erty"),
      caretAnchor = "Composable|Function()  // invocation"
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
        """,
      unavailableFixAt = listOf("fun getMy|Class(): Any"),
      caretAnchor = "Composable|Function()  // invocation"
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
        """,
      unavailableFixAt = listOf("fun getMy|Class(): Any"),
      caretAnchor = "fun|()"
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
        """,
      unavailableFixAt = listOf("fun getMy|Class(): Any"),
      caretAnchor = "prop|erty"
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
        """,
      unavailableFixAt = listOf("fun getMy|Class(): Any", "val prop|erty"),
      caretAnchor = "Composable|Function()  // invocation"
    )
  }

  private fun testQuickFix(
    @Language("kotlin") before: String,
    @Language("kotlin") after: String? = null,
    unavailableFixAt: List<String> = emptyList(),
    caretAnchor: String = "",
    expectedFunctionName: String? = null,
  ) {
    myFixture.configureByText("Test.kt", before.trimIndent())
    for (pattern in unavailableFixAt) {
      assertQuickFixNotAvailable(pattern)
    }
    if (after != null) {
      invokeQuickFix(caretAnchor, expectedFunctionName)
      myFixture.checkResult(after.trimIndent())
    }
  }

  private fun addExtraFile(
    extraFileName: String,
    @Language("kotlin") extraFileContent: String,
    configureExtraFile: (PsiFile.() -> Unit)? = null,
  ) {
    myFixture
      .addFileToProject(extraFileName, extraFileContent.trimIndent())
      .also { file ->
        if (configureExtraFile != null) {
          invokeAndWaitIfNeeded {
            runUndoTransparentWriteAction { file.configureExtraFile() }
          }
        }
      }
  }

  private fun invokeQuickFix(caretAnchor: String = "", expectedFunctionName: String? = null) {
    if (caretAnchor.isNotEmpty()) moveCaret(caretAnchor)

    myFixture.doHighlighting()

    val fixFilter: (IntentionAction) -> Boolean =
      if (expectedFunctionName != null) {
        { action ->
          action.text ==
            ComposeIdeBundle.message("compose.add.composable.to.element.name", expectedFunctionName)
        }
      }
      else {
        { action -> action.text.startsWith("Add '@Composable' to ") }
      }

    val action = myFixture.availableIntentions.singleOrNull(fixFilter)
    if (action == null) {
      val intentionTexts = myFixture.availableIntentions.joinToString(transform = IntentionAction::getText)
      fail("Could not find expected quick fix. Available intentions: $intentionTexts")
    }
    else {
      myFixture.launchAction(action)
    }
  }

  private fun assertQuickFixNotAvailable(caretAnchor: String = "") {
    if (caretAnchor.isNotEmpty()) moveCaret(caretAnchor)

    myFixture.doHighlighting()

    val action = myFixture.availableIntentions.filter { it.text.startsWith("Add '@Composable' to ") }
    if (action.isNotEmpty()) {
      fail("Found quick fix, not expected!")
    }
  }

  private fun moveCaret(caretAnchor: String) {
    val pipeIndex = caretAnchor.indexOf('|')
    check(pipeIndex != -1) { "Please include a pipe '|' to indicate caret position in: $caretAnchor" }

    val textToFind = caretAnchor.replace("|", "")
    val fileText = myFixture.file.text
    val startOffset = fileText.indexOf(textToFind)

    check(startOffset != -1) { "Could not find text '$textToFind' in the test file." }

    myFixture.editor.caretModel.moveToOffset(startOffset + pipeIndex)
  }

  fun CodeInsightTestFixture.stubComposableAnnotation(modulePath: String = "") {
    addFileToProject(
      "$modulePath/src/androidx/compose/runtime/Composable.kt",
      // language=kotlin
      """
      package androidx.compose.runtime
      @Target(
          AnnotationTarget.FUNCTION,
          AnnotationTarget.TYPE,
          AnnotationTarget.TYPE_PARAMETER,
          AnnotationTarget.PROPERTY_GETTER
      )
      annotation class Composable
      """
        .trimIndent(),
    )
  }
}
