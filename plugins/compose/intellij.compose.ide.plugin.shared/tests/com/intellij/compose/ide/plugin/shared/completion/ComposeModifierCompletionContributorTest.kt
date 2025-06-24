/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.intellij.compose.ide.plugin.shared.completion

import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.compose.ide.plugin.shared.util.CARET
import com.intellij.compose.ide.plugin.shared.util.configureByText
import com.intellij.compose.ide.plugin.shared.util.enableComposeInTest
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyAll
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFunction

@ApiStatus.Internal
abstract class ComposeModifierCompletionContributorTest : KotlinLightCodeInsightFixtureTestCase(), ExpectedPluginModeProvider {
  override fun setUp() {
    super.setUp()
    myFixture.enableComposeInTest()
    myFixture.addFileToProject(
      "Modifier.kt",
      """
        package androidx.compose.ui
            
        interface Modifier {
          fun function()
          companion object : Modifier {
            fun function() {}
          }
        }
            
        fun Modifier.extensionFunction(): Modifier { return this }
        fun Modifier.extensionFunctionReturnsNonModifier(): Int { return 1 }
      """.trimIndent(),
    )
    myFixture.addFileToProject(
      "myWidgetWithModifier.kt",
      """
        package com.example
  
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
  
        @Composable
        fun myWidgetWithModifier(modifier: Modifier) {}
      """.trimIndent(),
    )
  }

  fun testPrioritizeExtensionFunctionForMethodCalledOnModifierInAssignment() {
    // Prepare
    myFixture.configureByText(
      """
        package com.example
        
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        
        @Composable
        fun HomeScreen() {
          val m = Modifier.$CARET
        }
      """.trimIndent(),
    )

    // Do
    val lookupStrings = showCompletions()

    // Check
    lookupStrings.indexOf("extensionFunction") shouldBeLessThan lookupStrings.indexOf("function")
    lookupStrings.indexOf("extensionFunction") shouldBeLessThan lookupStrings.indexOf("extensionFunctionReturnsNonModifier")
    lookupStrings.indexOf("extensionFunctionReturnsNonModifier") shouldBeLessThan lookupStrings.indexOf("function")
  }

  fun testPrioritizeExtensionFunctionForMethodCalledOnModifierReturningFunction() {
    // Prepare
    myFixture.configureByText(
      """
        package com.example
        
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.extensionFunction
          
        @Composable
        fun HomeScreen() {
          val m = Modifier.extensionFunction().$CARET
        }
      """.trimIndent(),
    )

    // Do
    val lookupStrings = showCompletions()

    // Check
    lookupStrings.indexOf("extensionFunction") shouldBeLessThan lookupStrings.indexOf("function")
  }

  fun testPrioritizeExtensionFunctionForMethodCalledOnModifierInstance() {
    // Prepare
    myFixture.configureByText(
      """
        package com.example
        
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        
        @Composable
        fun HomeScreen(modifier: Modifier = Modifier) {
          modifier.$CARET
        }
      """.trimIndent(),
    )

    // Do
    val lookupStrings = showCompletions()

    // Check
    lookupStrings.indexOf("extensionFunction") shouldBeLessThan lookupStrings.indexOf("function")
  }

  fun testPrioritizeExtensionFunctionForMethodCalledOnModifierClass() {
    // Prepare
    myFixture.configureByText(
      """
        package com.example
        
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        
        @Composable
        fun HomeScreen() {
          Modifier.$CARET
        }
      """.trimIndent(),
    )

    // Do
    val lookupStrings = showCompletions()

    // Check
    lookupStrings.indexOf("extensionFunction") shouldBeLessThan lookupStrings.indexOf("function")
  }

  fun testModifierAsArgumentModifierPrefix() {
    // Prepare
    myFixture.configureByText(
      """
        package com.example
        
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        
        @Composable
        fun myWidget() {
            myWidgetWithModifier(Modifier.$CARET
        }
      """.trimIndent(),
    )

    // Do
    val lookupStrings = showCompletions()

    // Check
    lookupStrings shouldContain "extensionFunction"
    lookupStrings shouldContain "extensionFunctionReturnsNonModifier"
    lookupStrings.indexOf("extensionFunction") shouldBe 0

    // Do
    completeWith("extensionFunction")

    // Check
    myFixture.checkResult(
      """
        package com.example
  
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.extensionFunction
  
        @Composable
        fun myWidget() {
            myWidgetWithModifier(Modifier.extensionFunction()
        }
      """.trimIndent()
    )
  }

  fun testModifierAsArgumentNoModifierPrefix() {
    // Prepare
    myFixture.configureByText(
      """
        package com.example
        
        import androidx.compose.runtime.Composable
        
        @Composable
        fun myWidget() {
            myWidgetWithModifier($CARET
        }
      """.trimIndent(),
    )

    // Do
    val lookupStrings = showCompletions()

    // Check
    lookupStrings shouldContain "Modifier.extensionFunction"
    lookupStrings shouldNotContain "Modifier.extensionFunctionReturnsNonModifier"
    lookupStrings.indexOf("Modifier.extensionFunction") shouldBe 0

    // Do
    // to check that we still suggest "Modifier.extensionFunction" when the prefix doesn't much with the function name and only with "Modifier".
    // See [ComposeModifierCompletionContributor.ModifierLookupElement.getAllLookupStrings]
    myFixture.type("M")
    completeWith("extensionFunction")

    // Check
    myFixture.checkResult(
      """
        package com.example
  
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.extensionFunction
  
        @Composable
        fun myWidget() {
            myWidgetWithModifier(Modifier.extensionFunction()
        }
      """.trimIndent()
    )
  }

  fun testModifierAsNamedArgumentModifierPrefix() {
    // Prepare
    myFixture.configureByText(
      """
        package com.example
        
        import androidx.compose.runtime.Composable
        
        @Composable
        fun myWidget() {
            myWidgetWithModifier(modifier = $CARET
        }
      """.trimIndent(),
    )

    // Do
    val lookupStrings = showCompletions()

    // Check
    lookupStrings shouldContain "Modifier.extensionFunction"
    lookupStrings shouldNotContain "Modifier.extensionFunctionReturnsNonModifier"
    lookupStrings.indexOf("Modifier.extensionFunction") shouldBe 0

    // Do
    completeWith("extensionFunction")

    // Check
    myFixture.checkResult(
      """
        package com.example
  
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.extensionFunction
  
        @Composable
        fun myWidget() {
            myWidgetWithModifier(modifier = Modifier.extensionFunction()
        }
      """.trimIndent()
    )
  }

  fun testModifierAsNamedArgumentNoModifierPrefix() {
    // Prepare
    myFixture.configureByText(
      """
        package com.example
        
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        
        @Composable
        fun myWidget() {
            myWidgetWithModifier(modifier = Modifier.$CARET
        }
      """.trimIndent(),
    )

    // Do
    val lookupStrings = showCompletions()

    // Check
    lookupStrings shouldContain "extensionFunction"
    lookupStrings shouldContain "extensionFunctionReturnsNonModifier"
    lookupStrings.indexOf("extensionFunction") shouldBe 0

    // Do
    completeWith("extensionFunction")

    // Check
    myFixture.checkResult(
      """
        package com.example
  
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.extensionFunction
  
        @Composable
        fun myWidget() {
            myWidgetWithModifier(modifier = Modifier.extensionFunction()
        }
      """.trimIndent()
    )
  }

  fun testModifierAsPropertyNoModifierPrefix() {
    // Prepare
    myFixture.configureByText(
      """
        package com.example
        
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        
        @Composable
        fun myWidget() {
            val myModifier:Modifier = $CARET
        }
      """.trimIndent(),
    )

    // Do
    val lookupStrings = showCompletions()

    // Check
    lookupStrings shouldContain "Modifier.extensionFunction"
    // If the user didn't type `Modifier` don't suggest extensions that don't return Modifier.
    lookupStrings shouldNotContain "Modifier.extensionFunctionReturnsNonModifier"

    // Do
    myFixture.type("extensionFunction\t")

    // Check
    myFixture.checkResult(
      """
        package com.example
  
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.extensionFunction
  
        @Composable
        fun myWidget() {
            val myModifier:Modifier = Modifier.extensionFunction()
        }
      """.trimIndent()
    )
  }

  fun testModifierAsPropertyModifierPrefix() {
    // Prepare
    myFixture.configureByText(
      """
        package com.example
        
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        
        @Composable
        fun myWidget() {
            val myModifier:Modifier = Modifier.$CARET
        }
      """.trimIndent(),
    )

    // Do
    val lookupStrings = showCompletions()

    // Check
    lookupStrings shouldContain "extensionFunction"
    lookupStrings shouldContain "extensionFunctionReturnsNonModifier"
  }

  fun testModifierAsPropertyModifierReturningFunctionPrefix() {
    // Prepare
    myFixture.configureByText(
      """
        package com.example
        
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.extensionFunction
        
        @Composable
        fun myWidget() {
            val myModifier:Modifier = Modifier.extensionFunction().$CARET
        }
      """.trimIndent(),
    )

    // Do
    val lookupStrings = showCompletions()

    // Check
    lookupStrings shouldContain "extensionFunction"
    lookupStrings shouldContain "extensionFunctionReturnsNonModifier"
    lookupStrings.indexOf("extensionFunction") shouldBe 0
  }

  fun testModifierIsNotImported() {
    // Prepare
    myFixture.configureByText(
      """
        package com.example
        
        import androidx.compose.runtime.Composable
        
        @Composable
        fun myWidget() {
            myWidgetWithModifier(modifier = Modifier.$CARET
        }
      """.trimIndent(),
    )

    // Do
    val lookupStrings = showCompletions()

    // Check
    lookupStrings shouldContain "extensionFunction"
    lookupStrings.indexOf("extensionFunction") shouldBe 0

    // Do
    completeWith("extensionFunction")

    // Check
    myFixture.checkResult(
      """
        package com.example
  
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.extensionFunction
  
        @Composable
        fun myWidget() {
            myWidgetWithModifier(modifier = Modifier.extensionFunction()
        }
      """.trimIndent()
    )
  }

  fun testNewExtensionFunction() {
    // Prepare
    myFixture.configureByText(
      """
        package com.example
        
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.extensionFunction
        
        fun Modifier.foo() = extensionFunction().$CARET
      """
        .trimIndent(),
    )

    // Do
    val lookupStrings = showCompletions()

    // Check
    lookupStrings shouldContainInOrder listOf("extensionFunction", "extensionFunctionReturnsNonModifier")
  }

  // This is a regression test for https://issuetracker.google.com/issues/279049842
  @OptIn(KaAllowAnalysisOnEdt::class)
  fun testInvisibleObjectExtensionMethods() = allowAnalysisOnEdt {
    val mockResultSet = mockk<CompletionResultSet>(relaxUnitFun = true)

    fun mockCompletionResult(element: PsiElement): CompletionResult = mockk {
      every { lookupElement } returns mockk {
        every { psiElement } returns element
      }
    }

    // Notes from the original implementation:
    // This is a super-contrived example to try to regression test b/279049842. I haven't been able
    // to reproduce the real scenario, which
    // involves internal items coming from other libs/modules and relies on Kotlin plugin bugs which
    // (hopefully) would get fixed at some point.
    ApplicationManager.getApplication().invokeAndWait {
      myFixture.configureByText(
        """
          package com.example
          
          import androidx.compose.ui.Modifier
          
          object ParentObject {
            object VisibleChild {
              fun Modifier.visibleChildFunction(): Modifier = this
            }
          
            // Contrived for testing. The private wrapping object makes this not visible to the function below, and the
            // internal wrapping object is needed to pass checks in [ComposeModifierCompletionContributor].
            private object PrivateChild {
              internal object NestedChild {
                fun Modifier.notVisibleChildFunction(): Modifier = this
              }
            }
          }
          
          fun functionNeedingModifier(modifier: Modifier) {}
          
          fun doSomething() {
              functionNeedingModifier(modifier = MyModifier.)
          }
        """.trimIndent(),
      )
    }

    runReadAction {
      val functionCompletionCall =
        myFixture.findElementByText("functionNeedingModifier(modifier = MyModifier", KtCallExpression::class.java)
      val visibleChildFunctionCompletion =
        mockCompletionResult(myFixture.findElementByText("Modifier.visibleChildFunction():", KtFunction::class.java))
      consumerCompletionResultFromRemainingContributor(visibleChildFunctionCompletion, emptySet(), functionCompletionCall, mockResultSet)
      verifyAll { mockResultSet.passResult(visibleChildFunctionCompletion) }

      val notVisibleChildFunctionCompletion =
        mockCompletionResult(myFixture.findElementByText("Modifier.notVisibleChildFunction():", KtFunction::class.java))
      consumerCompletionResultFromRemainingContributor(notVisibleChildFunctionCompletion, emptySet(), functionCompletionCall, mockResultSet)
      confirmVerified(mockResultSet) // no more invocations
    }
  }

  fun showCompletions(): List<String> =
    myFixture.completeBasic().map { it.lookupString }

  fun completeWith(lookupString: String) {
    myFixture.lookup.currentItem = myFixture.lookupElements?.find { it.lookupString.contains(lookupString) }
    myFixture.finishLookup('\n')
  }
}
