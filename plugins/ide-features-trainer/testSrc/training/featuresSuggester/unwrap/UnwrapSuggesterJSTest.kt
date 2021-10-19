// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.featuresSuggester.unwrap

import junit.framework.TestCase
import training.featuresSuggester.FeatureSuggesterTestUtils.deleteSymbolAtCaret
import training.featuresSuggester.FeatureSuggesterTestUtils.insertNewLineAt
import training.featuresSuggester.FeatureSuggesterTestUtils.moveCaretToLogicalPosition
import training.featuresSuggester.FeatureSuggesterTestUtils.selectBetweenLogicalPositions
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.FeatureSuggesterTestUtils.typeAndCommit
import training.featuresSuggester.NoSuggestion
import training.featuresSuggester.UnwrapSuggesterTest

class UnwrapSuggesterJSTest : UnwrapSuggesterTest() {
  override val testingCodeFileName: String = "JavaScriptCodeExample.js"

  override fun `testUnwrap IF statement and get suggestion`() {
    with(myFixture) {
      moveCaretToLogicalPosition(22, 9)
      deleteSymbolAtCaret()
      selectBetweenLogicalPositions(lineStartIndex = 20, columnStartIndex = 49, lineEndIndex = 20, columnEndIndex = 3)
      deleteSymbolAtCaret()
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testUnwrap one-line IF and get suggestion`() {
    with(myFixture) {
      selectBetweenLogicalPositions(
        lineStartIndex = 56,
        columnStartIndex = 18,
        lineEndIndex = 56,
        columnEndIndex = 8
      )
      deleteSymbolAtCaret()
      moveCaretToLogicalPosition(56, 14)
      deleteSymbolAtCaret()
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testUnwrap IF with deleting multiline selection and get suggestion`() {
    with(myFixture) {
      selectBetweenLogicalPositions(lineStartIndex = 19, columnStartIndex = 22, lineEndIndex = 21, columnEndIndex = 5)
      deleteSymbolAtCaret()
      moveCaretToLogicalPosition(20, 9)
      deleteSymbolAtCaret()
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testUnwrap FOR and get suggestion`() {
    with(myFixture) {
      selectBetweenLogicalPositions(lineStartIndex = 47, columnStartIndex = 36, lineEndIndex = 47, columnEndIndex = 8)
      deleteSymbolAtCaret()
      moveCaretToLogicalPosition(50, 9)
      deleteSymbolAtCaret()
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testUnwrap WHILE and get suggestion`() {
    with(myFixture) {
      selectBetweenLogicalPositions(lineStartIndex = 52, columnStartIndex = 22, lineEndIndex = 52, columnEndIndex = 0)
      deleteSymbolAtCaret()
      moveCaretToLogicalPosition(55, 9)
      deleteSymbolAtCaret()
    }

    testInvokeLater(project) {
      assertSuggestedCorrectly()
    }
  }

  override fun `testUnwrap commented IF and don't get suggestion`() {
    with(myFixture) {
      insertNewLineAt(46, 8)
      typeAndCommit(
        """//if(true) {
              |//i++; j--;
              |//}""".trimMargin()
      )

      selectBetweenLogicalPositions(
        lineStartIndex = 46,
        columnStartIndex = 10,
        lineEndIndex = 46,
        columnEndIndex = 20
      )
      deleteSymbolAtCaret()
      moveCaretToLogicalPosition(48, 11)
      deleteSymbolAtCaret()
    }

    testInvokeLater(project) {
      TestCase.assertTrue(expectedSuggestion is NoSuggestion)
    }
  }

  override fun `testUnwrap IF written in string block and don't get suggestion`() {
    with(myFixture) {
      insertNewLineAt(46, 8)
      typeAndCommit(
        """let string = "if(true) {
              |i++; j--;
              |}"""".trimMargin()
      )

      selectBetweenLogicalPositions(
        lineStartIndex = 46,
        columnStartIndex = 22,
        lineEndIndex = 46,
        columnEndIndex = 32
      )
      deleteSymbolAtCaret()
      moveCaretToLogicalPosition(48, 14)
      deleteSymbolAtCaret()
    }

    testInvokeLater(project) {
      TestCase.assertTrue(expectedSuggestion is NoSuggestion)
    }
  }
}
