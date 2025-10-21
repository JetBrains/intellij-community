// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class K2RestartRangeTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode = KotlinPluginMode.K2

    override fun setUp() {
        super.setUp()
        Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
    }

    fun testRestartRangeTest() {
        myFixture.configureByText(
            "x.kt", """
            fun main() {
              val b = AA()
              val a = AA().<caret>
            }

            class AA {
                operator fun rangeTo(b: AA): List<AA> {
                    return listOf(this)
                } 
            }
        """.trimIndent()
        )

        myFixture.complete(CompletionType.BASIC, 0)

        // it is necessary to process typing on edt with rescheduling only for unit test
        //see com.intellij.codeInsight.completion.CompletionProgressIndicator.scheduleRestart
        @OptIn(KaAllowAnalysisOnEdt::class)
        allowAnalysisOnEdt {
            @OptIn(KaAllowAnalysisFromWriteAction::class)
            allowAnalysisFromWriteAction {
                myFixture.type(".")
                true
            }
        }

        val lookup = myFixture.lookup
        assertTrue(lookup != null)
        assertTrue(lookup.items.any { it.lookupString == "b" })
    }
}