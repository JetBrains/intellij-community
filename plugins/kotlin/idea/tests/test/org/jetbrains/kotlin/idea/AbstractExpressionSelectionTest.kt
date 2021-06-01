// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea

import org.jetbrains.kotlin.idea.core.util.CodeInsightUtils
import org.jetbrains.kotlin.idea.refactoring.IntroduceRefactoringException
import org.jetbrains.kotlin.idea.refactoring.selectElement
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.KotlinTestUtils

abstract class AbstractExpressionSelectionTest : KotlinLightCodeInsightFixtureTestCase() {
    fun doTestExpressionSelection(path: String) {
        myFixture.configureByFile(path)
        val expectedExpression = KotlinTestUtils.getLastCommentInFile(getFile() as KtFile)

        try {
            selectElement(getEditor(), getFile() as KtFile, listOf(CodeInsightUtils.ElementKind.EXPRESSION)) {
                assertNotNull("Selected expression mustn't be null", it)
                assertEquals(expectedExpression, it?.text)
            }
        } catch (e: IntroduceRefactoringException) {
            assertEquals(expectedExpression, "")
        }
    }
}
