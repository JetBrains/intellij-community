// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.completion.commands

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import java.util.concurrent.TimeUnit

class K2CommandCompletionSurroundWithTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode = KotlinPluginMode.K2

    override fun setUp() {
        super.setUp()
        Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    }

    fun testSurroundWithIf() {
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
        selectItem(elements.first { element -> element.lookupString.contains("Surround with 'If'", ignoreCase = true) })
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        myFixture.checkResult(
            """
            class A { 
                fun foo(a: String) {
                    if () {
                        println(a)
                    }
                } 
            }""".trimIndent()
        )
    }

    fun testNoSurround() {
        myFixture.configureByText(
            "x.kt", """
        class A { 
            fun foo(a: String) {
                println(a.<caret>)
            } 
        }
      """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertNull(elements.firstOrNull { element -> element.lookupString.contains("Surround with 'If'", ignoreCase = true) } )
    }

    private fun selectItem(item: LookupElement, completionChar: Char = 0.toChar()) {
        val lookup: LookupImpl = myFixture.lookup as LookupImpl
        lookup.setCurrentItem(item)
        if (LookupEvent.isSpecialCompletionChar(completionChar)) {
            lookup.finishLookup(completionChar)
        } else {
            myFixture.type(completionChar)
        }
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        PlatformTestUtil.waitForAllDocumentsCommitted(10, TimeUnit.SECONDS)
    }
}