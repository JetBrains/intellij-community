// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.training.ifs

import junit.framework.TestCase
import training.featuresSuggester.FeatureSuggesterTestUtils.insertNewLineAt
import training.featuresSuggester.FeatureSuggesterTestUtils.moveCaretToLogicalPosition
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.FeatureSuggesterTestUtils.typeAndCommit
import training.featuresSuggester.NoSuggestion
import training.featuresSuggester.SurroundWithSuggesterTest

class SurroundWithSuggesterKotlinTest : SurroundWithSuggesterTest() {
    override val testingCodeFileName = "KotlinCodeExample.kt"

    override fun getTestDataPath() = KotlinSuggestersTestUtils.testDataPath

    override fun `testSurround one statement with IF and get suggestion`() {
        with(myFixture) {
            insertNewLineAt(6)
            typeAndCommit("if () {")
            insertNewLineAt(8)
            typeAndCommit("}")
        }

        testInvokeLater(project) {
            assertSuggestedCorrectly()
        }
    }

    override fun `testSurround 2 statements with IF and add '}' at the line with second statement and get suggestion`() {
        with(myFixture) {
            insertNewLineAt(5)
            typeAndCommit("if (true){")
            moveCaretToLogicalPosition(7, 19)
            typeAndCommit("}")
        }

        testInvokeLater(project) {
            assertSuggestedCorrectly()
        }
    }

    override fun `testSurround all statements in block with IF and get suggestion`() {
        with(myFixture) {
            insertNewLineAt(5)
            typeAndCommit("if(){")
            insertNewLineAt(14)
            typeAndCommit("}")
        }

        testInvokeLater(project) {
            assertSuggestedCorrectly()
        }
    }

    // todo: investigate why this test is falling
    override fun `testSurround one statement with IF in one line and get suggestion`() {
        //        moveCaretToLogicalPosition(6, 7)
        //        typeAndCommit("if(1 > 2 ){")
        //        moveCaretRelatively(12, 0)
        //        typeAndCommit("}")
        //
        //        invokeLater {
        //            assertSuggestedCorrectly()
        //        }
    }

    override fun `testSurround statements with FOR and get suggestion`() {
        with(myFixture) {
            insertNewLineAt(6)
            typeAndCommit("for (i in 0..10) {")
            insertNewLineAt(13)
            typeAndCommit("}")
        }

        testInvokeLater(project) {
            assertSuggestedCorrectly()
        }
    }

    override fun `testSurround statements with WHILE and get suggestion`() {
        with(myFixture) {
            insertNewLineAt(7)
            typeAndCommit("while(false && true){")
            insertNewLineAt(10)
            typeAndCommit("}")
        }

        testInvokeLater(project) {
            assertSuggestedCorrectly()
        }
    }

    /**
     * This case must throw suggestion but not working now
     */
    fun `testSurround IfStatement with IF and don't get suggestion`() {
        with(myFixture) {
            insertNewLineAt(9)
            typeAndCommit("if (false && true){")
            insertNewLineAt(13)
            typeAndCommit("}")
        }

        testInvokeLater(project) {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    override fun `testSurround 0 statements with IF and don't get suggestion`() {
        with(myFixture) {
            insertNewLineAt(6)
            typeAndCommit("if (true) {    }")
        }

        testInvokeLater(project) {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    override fun `testWrite if() but add braces in another place and don't get suggestion`() {
        with(myFixture) {
            insertNewLineAt(6)
            typeAndCommit("if() ")
            moveCaretToLogicalPosition(7, 20)
            typeAndCommit("{}")
        }

        testInvokeLater(project) {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }
}
