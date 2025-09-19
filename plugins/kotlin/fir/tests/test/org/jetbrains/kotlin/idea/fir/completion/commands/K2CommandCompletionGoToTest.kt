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

class K2CommandCompletionGoToTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode = KotlinPluginMode.K2

    override fun setUp() {
        super.setUp()
        Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    }

    fun testGoToSuperMethod() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            open class TestSuper {
                open fun foo() {}
            
                class Child : TestSuper() {
                    override fun foo.<caret>() {
                        super.foo()
                        println()
                    }
                }
            }""".trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(elements.first { element -> element.lookupString.contains("Go to super", ignoreCase = true) })
        myFixture.checkResult(
            """
            open class TestSuper {
                open fun foo<caret>() {}
            
                class Child : TestSuper() {
                    override fun foo() {
                        super.foo()
                        println()
                    }
                }
            }""".trimIndent()
        )
    }

    fun testGoToDeclaration() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun main() {
                val a = "1"
                print(a.<caret>)
            }
            """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(elements.first { element -> element.lookupString.contains("Go to decl", ignoreCase = true) })
        myFixture.checkResult(
            """
            fun main() {
                val a<caret> = "1"
                print(a)
            }
            """.trimIndent()
        )
    }

    fun testCommandsOnlyGoToImplementation() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
        interface A{
        
            fun a..<caret>()
        
            class B : A{
        
                override fun a() {
        
                }
            }
        }
      """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(elements.first { element -> element.lookupString.contains("Go to impl", ignoreCase = true) })
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        myFixture.checkResult(
            """
        interface A{
        
            fun a()
        
            class B : A{
        
                override fun a<caret>() {
        
                }
            }
        }
      """.trimIndent()
        )
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