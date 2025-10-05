// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.imports

import com.intellij.codeInsight.daemon.impl.HighlightInfo.IntentionActionDescriptor
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import java.util.concurrent.Callable
import java.util.function.BiFunction

/**
 * Test how "Import class" quick fix behaves in presence of typing around/moving caret
 */
class ImportClassDuringTypingTest: KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    fun testImportHintMustStillBeAvailableAfterTypingBeforeTheReference() {
        myFixture.configureByText("Foo.kt", "class Foo(val ok: <caret>FileInputStream)")
        val editor = myFixture.editor
        assertHasImportHintAllOverUnresolvedReference()
        val offset = editor.getDocument().getText().indexOf("FileInputStream")
        for (i in 0..9) {
            getEditor().getCaretModel().moveToOffset(offset + i)
            myFixture.type(" ")
            assertHasImportHintAllOverUnresolvedReference()
        }
        for (i in 0..9) {
            getEditor().getCaretModel().moveToOffset(offset + 10 + i)
            myFixture.type("\n")
            assertHasImportHintAllOverUnresolvedReference()
        }
    }

    private fun assertHasImportHintAllOverUnresolvedReference() {
        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR).sortedWith(Segment.BY_START_OFFSET_THEN_END_OFFSET)
        assertNotEmpty(errors)
        val error = errors.get(0)
        assertEquals("[UNRESOLVED_REFERENCE] Unresolved reference 'FileInputStream'.", error.getDescription())
        assertTrue(error.hasHint())
        val errDesc =
            error.findRegisteredQuickFix(BiFunction { descriptor: IntentionActionDescriptor?, range: TextRange? ->
                if (descriptor!!.getAction().getText().startsWith("Import class")) descriptor else null
            })
        assertNotNull(errDesc)
        assertTrue(errDesc!!.getAction().isAvailable(getProject(), getEditor(), getFile()))
        for (i in error.getActualStartOffset()..<error.getActualEndOffset()) {
            getEditor().getCaretModel().moveToOffset(i)
            val errDescriptors = ReadAction.nonBlocking(Callable {
                ShowIntentionsPass.getActionsToShow(getEditor(), getFile()).errorFixesToShow
            }).submit(AppExecutorUtil.getAppExecutorService()).get()
            val importDesc = errDescriptors.find{ descriptor ->
                descriptor!!.getAction().getText().startsWith("Import class")
            }
            assertNotNull(i.toString(), importDesc)
            assertTrue(importDesc!!.getAction().isAvailable(getProject(), getEditor(), getFile()))
        }
    }

}