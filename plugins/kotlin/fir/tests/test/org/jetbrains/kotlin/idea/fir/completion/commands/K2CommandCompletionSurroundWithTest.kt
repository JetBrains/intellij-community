// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.completion.commands

import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class K2CommandCompletionSurroundWithTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode = KotlinPluginMode.K2

    override fun setUp() {
        super.setUp()
        Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    }

    fun testNoExceptionForPackages() {
        myFixture.configureByText(
            "x.kt", """
        package a.b.c.<caret>
        class A { 
            fun foo(a: String) {
                println(a)
            } 
        }
      """.trimIndent()
        )
        myFixture.completeBasic()
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
        selectItem(myFixture, elements.first { element -> element.lookupString.contains("Surround with 'If'", ignoreCase = true) })
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
}