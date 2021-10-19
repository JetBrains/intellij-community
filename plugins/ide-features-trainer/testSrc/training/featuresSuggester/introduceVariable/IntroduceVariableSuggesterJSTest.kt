// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.featuresSuggester.introduceVariable

import junit.framework.TestCase
import training.featuresSuggester.FeatureSuggesterTestUtils.copyCurrentSelection
import training.featuresSuggester.FeatureSuggesterTestUtils.cutBetweenLogicalPositions
import training.featuresSuggester.FeatureSuggesterTestUtils.deleteSymbolAtCaret
import training.featuresSuggester.FeatureSuggesterTestUtils.insertNewLineAt
import training.featuresSuggester.FeatureSuggesterTestUtils.moveCaretToLogicalPosition
import training.featuresSuggester.FeatureSuggesterTestUtils.pasteFromClipboard
import training.featuresSuggester.FeatureSuggesterTestUtils.selectBetweenLogicalPositions
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.FeatureSuggesterTestUtils.typeAndCommit
import training.featuresSuggester.IntroduceVariableSuggesterTest
import training.featuresSuggester.NoSuggestion

class IntroduceVariableSuggesterJSTest : IntroduceVariableSuggesterTest() {
  override val testingCodeFileName = "JavaScriptCodeExample.js"

  override fun `testIntroduce expression from IF and get suggestion`() {
    with(myFixture) {
      cutBetweenLogicalPositions(lineStartIndex = 20, columnStartIndex = 23, lineEndIndex = 20, columnEndIndex = 46)
      insertNewLineAt(20, 8)
      typeAndCommit("let flag =")
      pasteFromClipboard()
      moveCaretToLogicalPosition(21, 23)
      typeAndCommit(" flag")
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce full expression from method call and get suggestion`() {
    with(myFixture) {
      cutBetweenLogicalPositions(lineStartIndex = 21, columnStartIndex = 24, lineEndIndex = 21, columnEndIndex = 63)
      insertNewLineAt(21, 12)
      typeAndCommit("var value = ")
      pasteFromClipboard()
      moveCaretToLogicalPosition(22, 24)
      typeAndCommit("value")
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce part of expression from method call and get suggestion`() {
    with(myFixture) {
      cutBetweenLogicalPositions(lineStartIndex = 21, columnStartIndex = 33, lineEndIndex = 21, columnEndIndex = 24)
      insertNewLineAt(21, 12)
      typeAndCommit("let val = ")
      pasteFromClipboard()
      moveCaretToLogicalPosition(22, 24)
      typeAndCommit("val")
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce part of string expression from method call and get suggestion`() {
    with(myFixture) {
      cutBetweenLogicalPositions(lineStartIndex = 62, columnStartIndex = 30, lineEndIndex = 62, columnEndIndex = 15)
      insertNewLineAt(62, 8)
      typeAndCommit("const tring = ")
      pasteFromClipboard()
      moveCaretToLogicalPosition(63, 15)
      typeAndCommit("tring")
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce full expression from return statement and get suggestion`() {
    with(myFixture) {
      cutBetweenLogicalPositions(lineStartIndex = 63, columnStartIndex = 15, lineEndIndex = 63, columnEndIndex = 51)
      insertNewLineAt(63, 8)
      typeAndCommit("let bool= ")
      pasteFromClipboard()
      moveCaretToLogicalPosition(64, 15)
      typeAndCommit("bool")
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testIntroduce expression from method body using copy and backspace and get suggestion`() {
    with(myFixture) {
      selectBetweenLogicalPositions(
        lineStartIndex = 37,
        columnStartIndex = 30,
        lineEndIndex = 37,
        columnEndIndex = 40
      )
      copyCurrentSelection()
      selectBetweenLogicalPositions(
        lineStartIndex = 37,
        columnStartIndex = 30,
        lineEndIndex = 37,
        columnEndIndex = 40
      )
      deleteSymbolAtCaret()
      insertNewLineAt(37, 8)
      typeAndCommit("let output = ")
      pasteFromClipboard()
      moveCaretToLogicalPosition(38, 30)
      typeAndCommit("output")
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  /**
   * This case must throw suggestion but not working now
   */
  fun `testIntroduce part of string declaration expression and don't get suggestion`() {
    with(myFixture) {
      cutBetweenLogicalPositions(lineStartIndex = 61, columnStartIndex = 24, lineEndIndex = 61, columnEndIndex = 37)
      insertNewLineAt(61, 8)
      typeAndCommit("let trrr = ")
      pasteFromClipboard()
      moveCaretToLogicalPosition(62, 24)
      typeAndCommit("trrr")
    }

    testInvokeLater(project) {
      TestCase.assertTrue(expectedSuggestion is NoSuggestion)
    }
  }
}
