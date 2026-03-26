// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.quickfix

import com.intellij.codeInsight.intention.impl.preview.IntentionPreviewPopupUpdateProcessor.Companion.getPreviewText
import com.intellij.openapi.application.readAction
import com.intellij.codeInspection.LocalInspectionTool
import kotlinx.coroutines.runBlocking
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.ConvertToStringTemplateInspection
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class KotlinQuickFixIntentionPreviewTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

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
            "Convert concatenation to template",
            """
            fun main() {
                val code = "intention"
                println("Hello World!${'$'}code")
            }""".trimIndent()
        )
    }

    fun testDestructuringDeclaration() {
        @Suppress("UNCHECKED_CAST")
        val inspectionClass = Class.forName("org.jetbrains.kotlin.idea.k2.codeinsight.inspections.DestructureInspection")
                as Class<out LocalInspectionTool>
        myFixture.enableInspections(inspectionClass)
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
        val previewText = runBlocking { 
            readAction { getPreviewText(project, action, file, editor) } 
        }
        assertEquals(
            output, previewText
        )
    }
    
    override fun runFromCoroutine(): Boolean = true
}
