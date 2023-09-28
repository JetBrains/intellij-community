// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.highlighter

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.highlighting.HighlightUsagesHandler
import com.intellij.codeInsight.highlighting.highlightUsages
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.range
import com.intellij.testFramework.ExpectedHighlightingData
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.extractMarkerOffset
import java.util.concurrent.Callable

abstract class AbstractCustomHighlightUsageHandlerTest : KotlinLightCodeInsightFixtureTestCase() {

    companion object {
        // Not standard <caret> to leave it in text after configureByFile and remove manually after collecting highlighting information
        const val CARET_TAG = "~"
    }

    open fun doTest(unused: String) {
        myFixture.configureByFile(fileName())

        val editor = myFixture.editor

        val document = myFixture.editor.document
        val data = ExpectedHighlightingData(document, false, false, true, false)
        data.init()

        val caret = document.extractMarkerOffset(project, CARET_TAG)
        assert(caret != -1) { "Caret marker '${CARET_TAG}' expected" }
        editor.caretModel.moveToOffset(caret)

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

        val ranges = editor.markupModel.allHighlighters
            .filter { it.textAttributesKey == EditorColors.SEARCH_RESULT_ATTRIBUTES }
            .mapNotNull { it.range }

        val infos = ranges.toHashSet()
            .map {
                var startOffset = it.startOffset
                var endOffset = it.endOffset

                if (startOffset > caret) startOffset += CARET_TAG.length
                if (endOffset > caret) endOffset += CARET_TAG.length

                HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
                    .range(startOffset, endOffset)
                    .create()
            }

        data.checkResult(myFixture.file, infos, StringBuilder(document.text).insert(caret, CARET_TAG).toString())
    }
}