// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.editor

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.ScrollType
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.utils.coroutines.waitCoroutinesBlocking
import org.jetbrains.kotlin.idea.codeInsight.copyPaste.KotlinCopyPasteCoroutineScopeService
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.runAll

/**
 * Checks that a paste from one string literal into another keeps the scrolling position of the editor.
 */
class StringTemplatePasteScrollingTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun tearDown() {
        runAll(
            // Without this wait, KotlinCopyPasteReferenceProcessor finishes after the tear down if the test fails and uses invalid PSI
            { waitCoroutinesBlocking(KotlinCopyPasteCoroutineScopeService.getCoroutineScope(project)) },
            { super.tearDown() },
        )
    }

    fun testEscapedLineBreaks() = doTest(
        source = """val source = "<selection>a\nb\nc</selection>"""",
        target = """val target = "<caret>"""",
        expected = """val target = "a\nb\nc<caret>"""",
    )

    fun testEscapedLineBreaksWithEntries() = doTest(
        source = $$"""val source = "<selection>$a\n$b\n$c</selection>"""",
        target = """val target = "<caret>"""",
        expected = $$"""val target = "$a\n$b\n$c<caret>"""",
    )

    fun testEscapedLineBreaksToPrefixedString() = doTest(
        source = """val source = "<selection>a\nb\nc</selection>"""",
        target = """val target = $$"<caret>"""",
        expected = """val target = $$"a\nb\nc<caret>"""",
    )

    private fun doTest(source: String, target: String, expected: String) {
        myFixture.configureByText("source.kt", source)
        myFixture.performEditorAction(IdeActions.ACTION_COPY)

        myFixture.configureByText("target.kt", "$FILLER_BEFORE\n$target\n$FILLER_AFTER")
        val editor = myFixture.editor
        EditorTestUtil.setEditorVisibleSize(editor, 80, 10)
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        val scrollOffsetBeforePaste = editor.scrollingModel.verticalScrollOffset

        myFixture.performEditorAction(IdeActions.ACTION_PASTE)

        myFixture.checkResult("$FILLER_BEFORE\n$expected\n$FILLER_AFTER")
        assertEquals(
            "Unexpected scrolling after the paste",
            scrollOffsetBeforePaste,
            editor.scrollingModel.verticalScrollOffset,
        )
    }

    companion object {
        private val FILLER_BEFORE: String = (1..40).joinToString("\n") { "val v$it = $it" }
        private val FILLER_AFTER: String = (41..80).joinToString("\n") { "val v$it = $it" }
    }
}
