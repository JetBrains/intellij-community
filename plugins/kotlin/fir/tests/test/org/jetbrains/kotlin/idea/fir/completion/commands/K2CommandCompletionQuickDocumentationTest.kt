// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.completion.commands

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class K2CommandCompletionQuickDocumentationTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode = KotlinPluginMode.K2

    override fun setUp() {
        super.setUp()
        Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    }

    fun testClass() {
        myFixture.configureByText(
            "x.kt", """
        class A.<caret> { 
            fun foo(a: String) {
                var b = a
            } 
        }
      """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertNotNull(elements.firstOrNull() { element -> element.lookupString.contains("Quick Documentation", ignoreCase = true) })
    }

    fun testReference() {
        myFixture.configureByText(
            "x.kt", """
        class A { 
            fun foo(a: String) {
                print.<caret>(a)
            }
             
            fun print(a: String) {
                var b = a
            }
        }
      """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertNotNull(elements.firstOrNull() { element -> element.lookupString.contains("Quick Documentation", ignoreCase = true) })
    }

    fun testNoLocalVariable() {
        myFixture.configureByText(
            "x.kt", """
        class A { 
            fun foo() {
                val a.<caret> = "1"
                print(a)
            } 
        }
      """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertNull(elements.firstOrNull() { element -> element.lookupString.contains("Quick Documentation", ignoreCase = true) })
    }
}