// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor.Companion.getPreviewText
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.intentions.ConvertToStringTemplateInspection
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class KotlinQuickFixIntentionPreviewTest : KotlinLightCodeInsightFixtureTestCase() {
    fun testIntentionPreview() {
        doTest("""
            fun test(p: Int) {
                if (<caret>p == null) {
                    println("hello")
                }
            }""".trimIndent(),
            "Simplify comparison",
            """
            fun test(p: Int) {
                }""".trimIndent())
    }

    fun testConvertConcatenationToTemplate() {
        myFixture.enableInspections(ConvertToStringTemplateInspection())
        doTest(
            """
            fun main() {
                val code = "intention"
                println(<caret>"Hello World!" + code)
            }""".trimIndent(),
            "Convert 'String' concatenation to a template",
            """
            fun main() {
                val code = "intention"
                println("Hello World!${'$'}code")
            }""".trimIndent()
        )
    }

    fun testDestructuringDeclaration() {
        myFixture.enableInspections(ConvertToStringTemplateInspection())
        doTest(
            """
            data class X(val x: Int, val y: Int)
            
            val lambda : (X) -> Unit = { <caret>x -> println(x.x+x.y) }""".trimIndent(),
            "Use destructuring declaration",
            """
            data class X(val x: Int, val y: Int)

            val lambda : (X) -> Unit = { (x1, y) -> println(x1 + y) }""".trimIndent()
        )
    }

    private fun doTest(@Language("kotlin") input: String, actionName: String, @Language("kotlin") output: String) {
        myFixture.configureByText("Test.kt", input)
        val action = myFixture.findSingleIntention(actionName)
        val text = getPreviewText(project, action, file, editor)
        assertEquals(
            output, text
        )
    }
}