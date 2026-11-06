// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.training.ifs

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import training.featuresSuggester.FeatureSuggesterTestUtils.deleteSymbolAtCaret
import training.featuresSuggester.FeatureSuggesterTestUtils.insertNewLineAt
import training.featuresSuggester.FeatureSuggesterTestUtils.moveCaretRelatively
import training.featuresSuggester.FeatureSuggesterTestUtils.moveCaretToLogicalPosition
import training.featuresSuggester.FeatureSuggesterTestUtils.selectBetweenLogicalPositions
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.FeatureSuggesterTestUtils.typeAndCommit
import training.featuresSuggester.NoSuggestion
import training.featuresSuggester.featureSuggesterFixture

@TestApplication
class UnwrapSuggesterKotlinTest {
    private val suggester by featureSuggesterFixture(KotlinSuggestersTestUtils.testDataPath, "KotlinCodeExample.kt", "Unwrap")

    @Test
    fun `testUnwrap IF statement and get suggestion`(): Unit = suggester.runTest {
        with(myFixture) {
            moveCaretToLogicalPosition(11, 9)
            deleteSymbolAtCaret()
            selectBetweenLogicalPositions(lineStartIndex = 9, columnStartIndex = 42, lineEndIndex = 9, columnEndIndex = 3)
            deleteSymbolAtCaret()
        }

        testInvokeLater(project) {
            assertSuggestedCorrectly()
        }
    }

    @Test
    fun `testUnwrap one-line IF and get suggestion`(): Unit = suggester.runTest {
        with(myFixture) {
            selectBetweenLogicalPositions(lineStartIndex = 31, columnStartIndex = 23, lineEndIndex = 31, columnEndIndex = 8)
            deleteSymbolAtCaret()
            moveCaretRelatively(6, 0)
            deleteSymbolAtCaret()
        }

        testInvokeLater(project) {
            assertSuggestedCorrectly()
        }
    }

    @Test
    fun `testUnwrap IF with deleting multiline selection and get suggestion`(): Unit = suggester.runTest {
        with(myFixture) {
            selectBetweenLogicalPositions(lineStartIndex = 8, columnStartIndex = 23, lineEndIndex = 10, columnEndIndex = 5)
            deleteSymbolAtCaret()
            moveCaretToLogicalPosition(9, 9)
            deleteSymbolAtCaret()
        }

        testInvokeLater(project) {
            assertSuggestedCorrectly()
        }
    }

    @Test
    fun `testUnwrap FOR and get suggestion`(): Unit = suggester.runTest {
        with(myFixture) {
            selectBetweenLogicalPositions(lineStartIndex = 22, columnStartIndex = 34, lineEndIndex = 22, columnEndIndex = 9)
            deleteSymbolAtCaret()
            moveCaretToLogicalPosition(25, 13)
            deleteSymbolAtCaret()
        }

        testInvokeLater(project) {
            assertSuggestedCorrectly()
        }
    }

    @Test
    fun `testUnwrap WHILE and get suggestion`(): Unit = suggester.runTest {
        with(myFixture) {
            selectBetweenLogicalPositions(lineStartIndex = 27, columnStartIndex = 27, lineEndIndex = 27, columnEndIndex = 0)
            deleteSymbolAtCaret()
            moveCaretToLogicalPosition(30, 13)
            deleteSymbolAtCaret()
        }

        testInvokeLater(project) {
            assertSuggestedCorrectly()
        }
    }

    @Test
    fun `testUnwrap commented IF and don't get suggestion`(): Unit = suggester.runTest {
        with(myFixture) {
            insertNewLineAt(21, 12)
            typeAndCommit(
                """//if(true) {
              |//i++; j--;
              |//}""".trimMargin()
            )

            selectBetweenLogicalPositions(
                lineStartIndex = 21,
                columnStartIndex = 14,
                lineEndIndex = 21,
                columnEndIndex = 24
            )
            deleteSymbolAtCaret()
            moveCaretToLogicalPosition(23, 15)
            deleteSymbolAtCaret()
        }

        testInvokeLater(project) {
            assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    @Test
    fun `testUnwrap IF written in string block and don't get suggestion`(): Unit = suggester.runTest {
        with(myFixture) {
            insertNewLineAt(21, 12)
            typeAndCommit("val s = \"\"\"if(true) {\ni++\nj--\n}")

            selectBetweenLogicalPositions(
                lineStartIndex = 21,
                columnStartIndex = 23,
                lineEndIndex = 21,
                columnEndIndex = 33
            )
            deleteSymbolAtCaret()
            moveCaretToLogicalPosition(24, 18)
            deleteSymbolAtCaret()
        }

        testInvokeLater(project) {
            assertTrue(expectedSuggestion is NoSuggestion)
        }
    }
}
