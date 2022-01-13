// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.training.ifs

import training.featuresSuggester.FeatureSuggesterTestUtils.chooseCompletionItem
import training.featuresSuggester.FeatureSuggesterTestUtils.deleteTextBetweenLogicalPositions
import training.featuresSuggester.FeatureSuggesterTestUtils.invokeCodeCompletion
import training.featuresSuggester.FeatureSuggesterTestUtils.moveCaretToLogicalPosition
import training.featuresSuggester.FeatureSuggesterTestUtils.testInvokeLater
import training.featuresSuggester.FeatureSuggesterTestUtils.typeAndCommit
import training.featuresSuggester.FeatureSuggesterTestUtils.typeDelete
import training.featuresSuggester.NoSuggestion
import training.featuresSuggester.ReplaceCompletionSuggesterTest

class ReplaceCompletionSuggesterKotlinTest : ReplaceCompletionSuggesterTest() {
    override val testingCodeFileName = "KotlinCodeExample.kt"

    override fun getTestDataPath() = KotlinSuggestersTestUtils.testDataPath

    override fun `testDelete and type dot, complete method call, remove previous identifier and get suggestion`() {
        with(myFixture) {
            moveCaretToLogicalPosition(12, 20)
            deleteAndTypeDot()
            val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
            chooseCompletionItem(variants[0])
            repeat(5) { typeDelete() }
        }

        testInvokeLater(project) {
            assertSuggestedCorrectly()
        }
    }

    override fun `testCall completion, complete method call, remove previous identifier and get suggestion`() {
        with(myFixture) {
            moveCaretToLogicalPosition(64, 57)
            val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
            chooseCompletionItem(variants[3])
            deleteTextBetweenLogicalPositions(
                lineStartIndex = 64,
                columnStartIndex = 72,
                lineEndIndex = 64,
                columnEndIndex = 94
            )
        }

        testInvokeLater(project) {
            assertSuggestedCorrectly()
        }
    }

    override fun `testCall completion, complete with method call, add parameter to method call, remove previous identifier and get suggestion`() {
        with(myFixture) {
            moveCaretToLogicalPosition(64, 57)
            val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
            chooseCompletionItem(variants[3])
            typeAndCommit("132")
            deleteTextBetweenLogicalPositions(
                lineStartIndex = 64,
                columnStartIndex = 76,
                lineEndIndex = 64,
                columnEndIndex = 97
            )
        }

        testInvokeLater(project) {
            assertSuggestedCorrectly()
        }
    }

    override fun `testCall completion, complete with property, remove previous identifier and get suggestion`() {
        with(myFixture) {
            moveCaretToLogicalPosition(64, 30)
            val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
            chooseCompletionItem(variants[0])
            repeat(21) { typeDelete() }
        }

        testInvokeLater(project) {
            assertSuggestedCorrectly()
        }
    }

    override fun `testCall completion inside arguments list, complete method call, remove previous identifier and get suggestion`() {
        with(myFixture) {
            moveCaretToLogicalPosition(64, 88)
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
            moveCaretToLogicalPosition(64, 30)
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
            moveCaretToLogicalPosition(64, 57)
            val variants = invokeCodeCompletion() ?: error("Not found lookup elements")
            chooseCompletionItem(variants[3])
            deleteTextBetweenLogicalPositions(
                lineStartIndex = 65,
                columnStartIndex = 16,
                lineEndIndex = 65,
                columnEndIndex = 41
            )
        }

        testInvokeLater(project) {
            assertTrue(expectedSuggestion is NoSuggestion)
        }
    }
}
