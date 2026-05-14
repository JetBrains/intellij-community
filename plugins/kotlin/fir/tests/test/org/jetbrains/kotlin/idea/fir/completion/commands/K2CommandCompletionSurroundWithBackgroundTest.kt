// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.completion.commands

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.lookup.LookupElementCustomPreviewHolder
import com.intellij.modcommand.ActionContext
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.scripting.definitions.runReadAction

class K2CommandCompletionSurroundWithBackgroundTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode = KotlinPluginMode.K2

    override fun setUp() {
        super.setUp()
        Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    }

    override fun runInDispatchThread(): Boolean {
        return false
    }

    fun testSurroundWithTryCatchPreview() {
        myFixture.configureByText(
            "x.kt", """
        class A { 
            fun foo(a: String) {
                println(a).<caret>
            } 
        }
      """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        val item = elements.first { element -> element.lookupString.contains("Surround with 'Try / catch'", ignoreCase = true) }
        val previewHolder = item.`as`(LookupElementCustomPreviewHolder::class.java)
        runReadAction {
            val preview = previewHolder?.preview(ActionContext.from(myFixture.editor, myFixture.file)) as? IntentionPreviewInfo.CustomDiff
            assertEquals("""
                class A { 
                    fun foo(a: String) {
                        try {
                            println(a)
                        } catch (e: kotlin.Exception) {
                            throw e
                        }
                    } 
                }
            """.trimIndent(), preview?.modifiedText())
        }
    }
}