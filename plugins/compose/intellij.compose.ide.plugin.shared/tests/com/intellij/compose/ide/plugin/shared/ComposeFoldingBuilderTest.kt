/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.intellij.compose.ide.plugin.shared

import com.intellij.compose.ide.plugin.shared.util.enableComposeInTest
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

abstract class ComposeFoldingBuilderTest : KotlinLightCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

  private val testPath = PlatformTestUtil.getCommunityPath() + "/plugins/compose/intellij.compose.ide.plugin.shared/testData/folding"

  override fun setUp() {
    super.setUp()

    myFixture.enableComposeInTest()

    myFixture.addFileToProject(
      "src/androidx/compose/ui/Modifier.kt",
      // language=kotlin
      """
      package androidx.compose.ui
      interface Modifier {
        companion object : Modifier
        fun adjust(): Modifier = this
      }
      """
    )

  }

  fun `test basic composable arguments`() {
    myFixture.testFolding("$testPath/BasicComposableArguments.kt")
  }

  fun `test basic composable return type`() {
    myFixture.testFolding("$testPath/BasicComposableReturnType.kt")
  }

  fun `test basic conditional expressions`() {
    myFixture.testFolding("$testPath/BasicConditionalExpressions.kt")
  }

  fun `test basic inline function`() {
    myFixture.testFolding("$testPath/BasicInlineFunctions.kt")
  }

  fun `test basic modifier chain`() {
    myFixture.testFolding("$testPath/BasicModifierChain.kt")
  }

  fun `test basic multiple chains`() {
    myFixture.testFolding("$testPath/BasicMultipleChains.kt")
  }

  fun `test basic scope functions`() {
    myFixture.testFolding("$testPath/BasicScopeFunctions.kt")
  }

  fun `test basic suspend function`() {
    myFixture.testFolding("$testPath/BasicSuspendFunction.kt")
  }

  fun `test inline crossinline lambda`() {
    myFixture.testFolding("$testPath/InlineCrossinlineLambda.kt")
  }

  fun `test inline noinline lambda`() {
    myFixture.testFolding("$testPath/InlineNoinlineLambda.kt")
  }

  fun `test nested annotated lambdas`() {
    myFixture.testFolding("$testPath/NestedAnnotatedLambda.kt")
  }

  fun `test nested annotated non composable lambda`() {
    myFixture.testFolding("$testPath/NestedAnnotatedNonComposableLambda.kt")
  }

  fun `test nested composable functions`() {
    myFixture.testFolding("$testPath/NestedComposableFunctions.kt")
  }

  fun `test nested composable return type`() {
    myFixture.testFolding("$testPath/NestedComposableReturnType.kt")
  }

  fun `test nested explicit type lambda`() {
    myFixture.testFolding("$testPath/NestedExplicitTypeLambda.kt")
  }

  fun `test nested function in object`() {
    myFixture.testFolding("$testPath/NestedFunctionInObject.kt")
  }

  fun `test nested inherited composable`() {
    myFixture.testFolding("$testPath/NestedInheritedComposable.kt")
  }

  fun `test nested no fold composable return type`() {
    myFixture.testFolding("$testPath/NestedNoFoldComposableReturnType.kt")
  }

  fun `test nested no fold inline function`() {
    myFixture.testFolding("$testPath/NestedNoFoldInlineFunction.kt")
  }

  fun `test nested no fold inner function`() {
    myFixture.testFolding("$testPath/NestedNoFoldInnerFunction.kt")
  }

  fun `test nested no fold lambda`() {
    myFixture.testFolding("$testPath/NestedNoFoldLambda.kt")
  }

  fun `test no fold bad ending`() {
    myFixture.testFolding("$testPath/NoFoldBadEnding.kt")
  }

  fun `test no fold composable return type`() {
    myFixture.testFolding("$testPath/NoFoldComposableReturnType.kt")
  }

  fun `test no fold lambdas`() {
    myFixture.testFolding("$testPath/NoFoldPropertyLambda.kt")
  }

  fun `test no fold non modifier`() {
    myFixture.testFolding("$testPath/NoFoldNonModifier.kt")
  }

  fun `test no fold short chain`() {
    myFixture.testFolding("$testPath/NoFoldShortChain.kt")
  }

  fun `test no fold scope standard`() {
    myFixture.testFolding("$testPath/NoFoldNonComposableFunction.kt")
  }

  fun `test property getter`() {
    myFixture.testFolding("$testPath/PropertyGetter.kt")
  }

  fun `test property lambda`() {
    myFixture.testFolding("$testPath/PropertyLambda.kt")
  }
}
