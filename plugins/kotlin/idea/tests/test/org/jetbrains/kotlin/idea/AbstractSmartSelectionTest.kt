// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea

import org.jetbrains.kotlin.idea.core.util.CodeInsightUtils
import org.jetbrains.kotlin.idea.refactoring.getExpressionShortText
import org.jetbrains.kotlin.idea.refactoring.getSmartSelectSuggestions
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.idea.test.KotlinTestUtils

@Suppress("DEPRECATION")
abstract class AbstractSmartSelectionTest : KotlinLightCodeInsightFixtureTestCase() {
    fun doTestSmartSelection(path: String) {
        myFixture.configureByFile(path)

        val expectedResultText = KotlinTestUtils.getLastCommentInFile(getFile() as KtFile)
        val elements = getSmartSelectSuggestions(getFile(), getEditor().caretModel.offset, CodeInsightUtils.ElementKind.EXPRESSION)
        assertEquals(expectedResultText, elements.joinToString(separator = "\n", transform = ::getExpressionShortText))
    }
}
