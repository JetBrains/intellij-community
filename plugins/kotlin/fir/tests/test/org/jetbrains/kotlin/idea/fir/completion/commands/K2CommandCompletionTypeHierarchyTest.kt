// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.completion.commands

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class K2CommandCompletionTypeHierarchyTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode = KotlinPluginMode.K2

    override fun setUp() {
        super.setUp()
        Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    }

    fun testClass() {
        myFixture.configureByText(
            "x.kt", """
        class A <caret>{ 
            fun foo() {
            } 
        }
      """.trimIndent()
        )
        myFixture.type(".")
        val elements = myFixture.completeBasic()
        assertNotNull(elements.firstOrNull() { element -> element.lookupString.contains("Type Hierarchy", ignoreCase = true) })
    }

    fun testClassType() {
        myFixture.configureByText(
            "x.kt", """
        class A { 
            fun foo() {
                val x: Int<caret> = 10
            } 
        }
      """.trimIndent()
        )
        myFixture.type(".")
        val elements = myFixture.completeBasic()
        assertNotNull(elements.firstOrNull() { element -> element.lookupString.contains("Type Hierarchy", ignoreCase = true) })
    }

    fun testClassTypeBroken() {
        myFixture.configureByText(
            "x.kt", """
        class A { 
            fun foo() {
                val x: Intaaaaaaaaaaaaa<caret> = 10
            } 
        }
      """.trimIndent()
        )
        myFixture.type(".")
        val elements = myFixture.completeBasic()
        assertNull(elements.firstOrNull() { element -> element.lookupString.contains("Type Hierarchy", ignoreCase = true) })
    }
}