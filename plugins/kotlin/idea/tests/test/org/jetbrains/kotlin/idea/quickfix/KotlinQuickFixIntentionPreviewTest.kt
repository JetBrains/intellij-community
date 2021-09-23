// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor.Companion.getPreviewText
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class KotlinQuickFixIntentionPreviewTest : KotlinLightCodeInsightFixtureTestCase() {
    fun testIntentionPreview() {
        @Language("kotlin")
        val sample = """
            fun test(p: Int) {
                if (<caret>p == null) {
                    println("hello")
                }
            }""".trimIndent()
        myFixture.configureByText("Test.kt", sample)
        val action = myFixture.findSingleIntention("Simplify comparison")
        val text = getPreviewText(project, action, file, editor)
        assertEquals("""
            fun test(p: Int) {
                }""".trimIndent(), text
        )
    }

}