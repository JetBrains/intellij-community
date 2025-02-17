// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class K2CommandCompletionTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode = KotlinPluginMode.K2

    fun testRedCompletion() {
        Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun main() {
              val a: String = 1.<caret>
            }
        """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(elements.first { element -> element.lookupString.contains("Change type of", ignoreCase = true) })
        myFixture.checkResult(
            """
            fun main() {
              val a: Int = 1
            }
        """.trimIndent()
        )
    }


    fun testFormat() {
        Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun main() {
              val a:                                                    String = "1"
            }.<caret>
        """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(elements.first { element -> element.lookupString.equals("Format", ignoreCase = true) })
        myFixture.checkResult(
            """
            fun main() {
                val a: String = "1"
            }
        """.trimIndent()
        )
    }

    fun testComment() {
        Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun main() {
              val a: String = "1".<caret>
            }
        """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(elements.first { element -> element.lookupString.contains("Comment", ignoreCase = true) })
        myFixture.checkResult(
            """
            fun main() {
            //  val a: String = "1"
            }
        """.trimIndent()
        )
    }

    fun testCommentPsiElement() {
        Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun main() {
              val a: String = "1"
            }.<caret>
        """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(elements.first { element -> element.lookupString.contains("Comment element", ignoreCase = true) })
        myFixture.checkResult(
            """
            //fun main() {
            //  val a: String = "1"
            //}
        """.trimIndent()
        )
    }

    fun testDeletePsiElement() {
        Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun main() {
              val a: String = "1"
            }.<caret>
        """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(elements.first { element -> element.lookupString.contains("Delete", ignoreCase = true) })
        myFixture.checkResult(
            ""
        )
    }

    fun testGenerate() {
        Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            class A{
                .<caret>    
            }
            """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(elements.first { element -> element.lookupString.contains("Generate 'Secondary", ignoreCase = true) })
        myFixture.checkResult(
            """
            class A{
                constructor()
            }
            """.trimIndent()
        )
    }

    fun testCopyFqn() {
        Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            class A.<caret>{
            
            }
            """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(elements.first { element -> element.lookupString.contains("Copy ref", ignoreCase = true) })
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
        myFixture.checkResult(
            """
            class AA{
            
            }
            """.trimIndent()
        )
    }

    fun testGoToDeclaration() {
        Registry.get("ide.completion.command.enabled").setValue(true, getTestRootDisposable())
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
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
        myFixture.checkResult(
            """
            fun main() {
                val <caret>a = "1"
                print(a)
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
    }

}