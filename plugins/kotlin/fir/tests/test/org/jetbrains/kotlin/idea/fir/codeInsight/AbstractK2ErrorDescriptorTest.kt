// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.codeInsight

import com.intellij.codeInsight.hint.TooltipRenderer
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.ex.ErrorStripTooltipRendererProvider
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.application
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class AbstractK2ErrorDescriptorTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun runInDispatchThread(): Boolean = false

    fun doTest(unused: String) {
        val fileName = fileName()

        var tooltipText: String? = null

        val latch = CountDownLatch(1)

        application.invokeAndWait {
            myFixture.configureByFile(fileName)

            val editorMarkupModel = myFixture.editor.markupModel as EditorMarkupModel
            val errorStripTooltipRendererProvider = editorMarkupModel.errorStripTooltipRendererProvider

            Disposer.register(testRootDisposable) {
                editorMarkupModel.errorStripTooltipRendererProvider = errorStripTooltipRendererProvider
            }

            myFixture.doHighlighting()

            // delegate error strip tooltip renderer provider to grab tooltip text
            editorMarkupModel.errorStripTooltipRendererProvider = object : ErrorStripTooltipRendererProvider {
                override fun calcTooltipRenderer(highlighters: Collection<RangeHighlighter?>): TooltipRenderer? =
                    errorStripTooltipRendererProvider.calcTooltipRenderer(highlighters)

                override fun calcTooltipRenderer(text: @NlsContexts.Tooltip String): TooltipRenderer {
                    tooltipText = text
                    latch.countDown()
                    return errorStripTooltipRendererProvider.calcTooltipRenderer(text)
                }

                override fun calcTooltipRenderer(
                    text: @NlsContexts.Tooltip String,
                    width: Int
                ): TooltipRenderer {
                    tooltipText = text
                    latch.countDown()
                    return errorStripTooltipRendererProvider.calcTooltipRenderer(text, width)
                }
            }

            myFixture.performEditorAction(IdeActions.ACTION_SHOW_ERROR_DESCRIPTION)
        }


        val description =
            InTextDirectivesUtils.findStringWithPrefixes(myFixture.file.text, DESCRIPTION_DIRECTIVE)

        // there are several steps to show popup like schedule alarm, invokeLater, etc.
        latch.await(5, TimeUnit.SECONDS)

        assertEquals(description, tooltipText?.let { StringUtil.stripHtml(it, true) })
    }

    companion object {
        private const val DESCRIPTION_DIRECTIVE: String = "DESCRIPTION:"
    }
}
