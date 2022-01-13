// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.training.ifs

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

/**
 * Note: when user is declaring variable and it's name starts with any language keyword suggestion will not be thrown
 */
class IntroduceVariableSuggesterKotlinTest : IntroduceVariableSuggesterTest() {
    override val testingCodeFileName = "KotlinCodeExample.kt"

    override fun getTestDataPath() = KotlinSuggestersTestUtils.testDataPath

    override fun `testIntroduce expression from IF and get suggestion`() {
        with(myFixture) {
            cutBetweenLogicalPositions(lineStartIndex = 9, columnStartIndex = 24, lineEndIndex = 9, columnEndIndex = 39)
            insertNewLineAt(9, 8)
            typeAndCommit("val flag =")
            pasteFromClipboard()
            moveCaretToLogicalPosition(10, 24)
            typeAndCommit(" flag")
        }

        testInvokeLater(project) {
            assertSuggestedCorrectly()
        }
    }

    override fun `testIntroduce full expression from method call and get suggestion`() {
        with(myFixture) {
            cutBetweenLogicalPositions(lineStartIndex = 10, columnStartIndex = 37, lineEndIndex = 10, columnEndIndex = 20)
            insertNewLineAt(10, 12)
            typeAndCommit("var temp = ")
            pasteFromClipboard()
            moveCaretToLogicalPosition(11, 20)
            typeAndCommit("temp")
        }

        testInvokeLater(project) {
            assertSuggestedCorrectly()
        }
    }

    override fun `testIntroduce part of expression from method call and get suggestion`() {
        with(myFixture) {
            cutBetweenLogicalPositions(lineStartIndex = 10, columnStartIndex = 29, lineEndIndex = 10, columnEndIndex = 20)
            insertNewLineAt(10, 12)
            typeAndCommit("val abcbcd = ")
            pasteFromClipboard()
            moveCaretToLogicalPosition(11, 20)
            typeAndCommit("abcbcd")
        }

        testInvokeLater(project) {
            assertSuggestedCorrectly()
        }
    }

    override fun `testIntroduce part of string expression from method call and get suggestion`() {
        with(myFixture) {
            cutBetweenLogicalPositions(lineStartIndex = 37, columnStartIndex = 35, lineEndIndex = 37, columnEndIndex = 46)
            insertNewLineAt(37, 12)
            typeAndCommit("val serr = ")
            pasteFromClipboard()
            moveCaretToLogicalPosition(38, 35)
            typeAndCommit("serr")
        }

        testInvokeLater(project) {
            assertSuggestedCorrectly()
        }
    }

    override fun `testIntroduce full expression from return statement and get suggestion`() {
        with(myFixture) {
            cutBetweenLogicalPositions(lineStartIndex = 50, columnStartIndex = 23, lineEndIndex = 50, columnEndIndex = 67)
            insertNewLineAt(50, 16)
            typeAndCommit("val bool=")
            pasteFromClipboard()
            moveCaretToLogicalPosition(51, 23)
            typeAndCommit("bool")
        }

        testInvokeLater(project) {
            assertSuggestedCorrectly()
        }
    }

    override fun `testIntroduce expression from method body using copy and backspace and get suggestion`() {
        with(myFixture) {
            selectBetweenLogicalPositions(
                lineStartIndex = 28,
                columnStartIndex = 24,
                lineEndIndex = 28,
                columnEndIndex = 29
            )
            copyCurrentSelection()
            selectBetweenLogicalPositions(
                lineStartIndex = 28,
                columnStartIndex = 24,
                lineEndIndex = 28,
                columnEndIndex = 29
            )
            deleteSymbolAtCaret()
            insertNewLineAt(28, 16)
            typeAndCommit("var output =")
            pasteFromClipboard()
            moveCaretToLogicalPosition(29, 24)
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
            cutBetweenLogicalPositions(lineStartIndex = 40, columnStartIndex = 22, lineEndIndex = 40, columnEndIndex = 46)
            insertNewLineAt(40, 12)
            typeAndCommit("val string = ")
            pasteFromClipboard()
            moveCaretToLogicalPosition(41, 22)
            typeAndCommit("string")
        }

        testInvokeLater(project) {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }
}
