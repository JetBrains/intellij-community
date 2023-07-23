// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.codeInsight.highlighting.highlightUsages
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.idea.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import java.util.concurrent.Callable

abstract class AbstractHighlightExitPointsTest : KotlinLightCodeInsightFixtureTestCase() {

    open fun doTest(unused: String) {
        myFixture.configureByFile(fileName())

        val editor = myFixture.editor
        val customHandler =
            HighlightUsagesHandler.createCustomHandler<PsiElement>(editor, myFixture.file)

        if (customHandler != null) {
            ReadAction.nonBlocking(Callable {
                customHandler.highlightUsages()
            }).submit(AppExecutorUtil.getAppExecutorService())
                .get()
        } else {
            DumbService.getInstance(project).withAlternativeResolveEnabled(Runnable {
                highlightUsages(project, editor, file)
            })
        }

        val text = myFixture.file.text
        val expectedToBeHighlighted = InTextDirectivesUtils.findLinesWithPrefixesRemoved(text, "//HIGHLIGHTED:")
        val searchResultsTextAttributes =
            EditorColorsManager.getInstance().globalScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)
        val highlighters = editor.markupModel.allHighlighters
            .filter { it.textAttributes == searchResultsTextAttributes }
        val actual = highlighters.map { text.substring(it.startOffset, it.endOffset) }
        assertEquals(expectedToBeHighlighted, actual)
    }
}