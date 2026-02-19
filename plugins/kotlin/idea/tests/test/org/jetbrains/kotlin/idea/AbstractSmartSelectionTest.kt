// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea

import org.jetbrains.kotlin.idea.codeinsight.utils.getExpressionShortText
import org.jetbrains.kotlin.idea.refactoring.getSmartSelectSuggestions
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.util.ElementKind
import org.jetbrains.kotlin.psi.KtFile

abstract class AbstractSmartSelectionTest : KotlinLightCodeInsightFixtureTestCase() {
    fun doTestSmartSelection(path: String) {
        myFixture.configureByFile(path)

        val expectedResultText = KotlinTestUtils.getLastCommentInFile(file as KtFile)
        val elements = getSmartSelectSuggestions(file, editor.caretModel.offset, ElementKind.EXPRESSION)
        assertEquals(expectedResultText, elements.joinToString(separator = "\n", transform = ::getExpressionShortText))
    }
}
