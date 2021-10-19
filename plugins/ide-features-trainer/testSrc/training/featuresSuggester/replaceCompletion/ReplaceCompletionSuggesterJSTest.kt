// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.featuresSuggester.replaceCompletion

import training.featuresSuggester.FeatureSuggesterTestUtils.chooseCompletionItem
import training.featuresSuggester.FeatureSuggesterTestUtils.deleteTextBetweenLogicalPositions
import training.featuresSuggester.FeatureSuggesterTestUtils.invokeCodeCompletion
import training.featuresSuggester.FeatureSuggesterTestUtils.moveCaretToLogicalPosition
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.FeatureSuggesterTestUtils.typeAndCommit
import training.featuresSuggester.FeatureSuggesterTestUtils.typeDelete
import training.featuresSuggester.NoSuggestion
import training.featuresSuggester.ReplaceCompletionSuggesterTest

class ReplaceCompletionSuggesterJSTest : ReplaceCompletionSuggesterTest() {
  override val testingCodeFileName = "JavaScriptCodeExample.js"

  override fun `testDelete and type dot, complete method call, remove previous identifier and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(24, 21)
      deleteAndTypeDot()
      val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
      chooseCompletionItem(variants[1])
      repeat(5) { typeDelete() }
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testCall completion, complete method call, remove previous identifier and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(72, 53)
      val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
      chooseCompletionItem(variants[0])
      deleteTextBetweenLogicalPositions(
        lineStartIndex = 72,
        columnStartIndex = 68,
        lineEndIndex = 72,
        columnEndIndex = 90
      )
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testCall completion, complete with method call, add parameter to method call, remove previous identifier and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(72, 53)
      val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
      chooseCompletionItem(variants[0])
      typeAndCommit("123")
      deleteTextBetweenLogicalPositions(
        lineStartIndex = 72,
        columnStartIndex = 72,
        lineEndIndex = 72,
        columnEndIndex = 93
      )
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testCall completion, complete with property, remove previous identifier and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(72, 26)
      val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
      chooseCompletionItem(variants[1])
      repeat(21) { typeDelete() }
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testCall completion inside arguments list, complete method call, remove previous identifier and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(72, 84)
      val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
      chooseCompletionItem(variants[0])
      repeat(15) { typeDelete() }
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testCall completion, type additional characters, complete, remove previous identifier and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(72, 26)
      invokeCodeCompletion()
      typeAndCommit("cycles")
      val variants = lookupElements ?: error("Not found lookup elements")
      chooseCompletionItem(variants[0])
      repeat(22) { typeDelete() }
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testCall completion, complete method call, remove another equal identifier and don't get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(72, 53)
      val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
      chooseCompletionItem(variants[0])
      deleteTextBetweenLogicalPositions(
        lineStartIndex = 73,
        columnStartIndex = 12,
        lineEndIndex = 73,
        columnEndIndex = 37
      )
    }

    testInvokeLater(project) {
      assertTrue(expectedSuggestion is NoSuggestion)
    }
  }
}
