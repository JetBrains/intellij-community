// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.completion.commands

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.command.CommandCompletionLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class K2CommandCompletionTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode = KotlinPluginMode.K2

    override fun setUp() {
        super.setUp()
        Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
    }

    fun testRedCompletion() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun main() {
              val a: String = 1.<caret>
            }
        """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(myFixture, elements.first { element -> element.lookupString.contains("Change type of", ignoreCase = true) })
        myFixture.checkResult(
            """
            fun main() {
              val a: Int = 1
            }
        """.trimIndent()
        )
    }

    fun testChangeSignature() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
        class A { 
            fun foo() <caret>{
            } 
        }
      """.trimIndent()
        )
        myFixture.doHighlighting()
        myFixture.type(".")
        val elements = myFixture.completeBasic()
        assertNotNull(elements.firstOrNull() { element -> element.lookupString.contains("Change Sign", ignoreCase = true) })
    }

    fun testRenameParameter() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt",
            """
            class A { 
                fun foo(a<caret>: String){
                } 
            }
            """.trimIndent()
        )
        myFixture.doHighlighting()
        myFixture.type(".")
        val elements = myFixture.completeBasic()
        val element = elements
            .firstOrNull { element -> element.lookupString.contains("Rename", ignoreCase = true) }
            ?.`as`(CommandCompletionLookupElement::class.java)
        assertNotNull(element)
        assertEquals(TextRange(23, 24), element?.highlighting?.range)
    }

    fun testRenameMethod() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
        class A { 
            fun foo() <caret>{
            } 
        }
      """.trimIndent()
        )
        myFixture.doHighlighting()
        myFixture.type(".")
        val elements = myFixture.completeBasic()
        val lookupElement = elements
            .firstOrNull { element -> element.lookupString.contains("rename", ignoreCase = true) }
            ?.`as`(CommandCompletionLookupElement::class.java)
        assertNotNull(lookupElement)
        assertEquals(TextRange(19, 22), (lookupElement as CommandCompletionLookupElement).highlighting?.range)
    }

    fun testRenameMethod2() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
        class A { 
            fun foo() {
            }<caret> 
        }
      """.trimIndent()
        )
        myFixture.doHighlighting()
        myFixture.type(".")
        val elements = myFixture.completeBasic()
        val lookupElement = elements
            .firstOrNull { element -> element.lookupString.contains("rename", ignoreCase = true) }
            ?.`as`(CommandCompletionLookupElement::class.java)
        assertNotNull(lookupElement)
        assertEquals(TextRange(19, 22), (lookupElement as CommandCompletionLookupElement).highlighting?.range)
    }

    fun testRenameMethod3() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
        class A { 
            fun foo<caret>() {
            }
        }
      """.trimIndent()
        )
        myFixture.doHighlighting()
        myFixture.type(".")
        val elements = myFixture.completeBasic()
        val lookupElement = elements
            .firstOrNull { element -> element.lookupString.contains("rename", ignoreCase = true) }
            ?.`as`(CommandCompletionLookupElement::class.java)
        assertNotNull(lookupElement)
        assertEquals(TextRange(19, 22), (lookupElement as CommandCompletionLookupElement).highlighting?.range)
    }

    fun testRenameMethod4() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
        class A { 
            fun foo<caret>(): String  = "1"
        }
      """.trimIndent()
        )
        myFixture.doHighlighting()
        myFixture.type(".")
        val elements = myFixture.completeBasic()
        val lookupElement = elements
            .firstOrNull { element -> element.lookupString.contains("rename", ignoreCase = true) }
            ?.`as`(CommandCompletionLookupElement::class.java)
        assertNotNull(lookupElement)
        assertEquals(TextRange(19, 22), (lookupElement as CommandCompletionLookupElement).highlighting?.range)
    }

    fun testRenameMethod5() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
        class A { 
            fun foo() <caret>: String  = "1"
        }
      """.trimIndent()
        )
        myFixture.doHighlighting()
        myFixture.type(".")
        val elements = myFixture.completeBasic()
        val lookupElement = elements
            .firstOrNull { element -> element.lookupString.contains("rename", ignoreCase = true) }
            ?.`as`(CommandCompletionLookupElement::class.java)
        assertNotNull(lookupElement)
        assertEquals(TextRange(19, 22), (lookupElement as CommandCompletionLookupElement).highlighting?.range)
    }

    fun testRenameClass() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
        class A<caret> { 
            fun foo() {
            } 
        }
      """.trimIndent()
        )
        myFixture.doHighlighting()
        myFixture.type(".")
        val elements = myFixture.completeBasic()
        assertNotNull(elements.firstOrNull() { element -> element.lookupString.contains("rename", ignoreCase = true) })
    }


    fun testFormat() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun main() {
              val a:                                                    String = "1"
            }.<caret>
        """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(myFixture, elements.first { element -> element.lookupString.equals("Reformat code", ignoreCase = true) })
        myFixture.checkResult(
            """
            fun main() {
                val a: String = "1"
            }
        """.trimIndent()
        )
    }

    fun testCommentPsiElementByLine() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun main() {
              val a: String = "1"
            }.<caret>
        """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(myFixture, elements.first { element -> element.lookupString.contains("Comment with line", ignoreCase = true) })
        myFixture.checkResult(
            """
            //fun main() {
            //  val a: String = "1"
            //}
        """.trimIndent()
        )
    }

    fun testCommentPsiElementByBlock() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun main() {
              val a: String = "1"
            }.<caret>
        """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(myFixture, elements.first { element -> element.lookupString.contains("Comment with block", ignoreCase = true) })
        myFixture.checkResult(
            """
            /*
            fun main() {
              val a: String = "1"
            }*/
            
        """.trimIndent()
        )
    }

    fun testDeletePsiElement() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun main() {
              val a: String = "1"
            }.<caret>
        """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(myFixture, elements.first { element -> element.lookupString.contains("Delete", ignoreCase = true) })
        myFixture.checkResult(
            ""
        )
    }

    fun testGenerate() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            class A{
                .<caret>    
            }
            """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(myFixture, elements.first { element -> element.lookupString.contains("Generate 'Secondary", ignoreCase = true) })
        myFixture.checkResult(
            """
            class A{
                constructor()
            }
            """.trimIndent()
        )
    }

    fun testEmptyFile() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            val<caret>
            """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertTrue(elements.isNotEmpty())
    }

    fun testCopyFqn() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            class A.<caret>{
            
            }
            """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(myFixture, elements.first { element -> element.lookupString.contains("Copy ref", ignoreCase = true) })
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
        myFixture.checkResult(
            """
            class AA{
            
            }
            """.trimIndent()
        )
    }

    fun testCommandsOnlyGoToImplementationNotFound() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
        interface A{
            fun a..<caret>()
        }
      """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertFalse(elements.any { element -> element.lookupString.contains("Go to impl", ignoreCase = true) })
    }

    fun testIntroduceParameter() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun foo() {
            
                val a = "1".<caret>
            }""".trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(myFixture, elements.first { element -> element.lookupString.contains("Introduce parameter", ignoreCase = true) })
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
        myFixture.checkResult(
            """
            fun foo(string: String) {
            
                val a = string
            }""".trimIndent()
        )
    }

    fun testExtractMethodInChainCall() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            class A {
                fun f() : String = ""
                
                fun f() {
                    A().f().<caret>
                }
            }
        """.trimIndent())

        val elements = myFixture.completeBasic()
        assertTrue(elements.any { element -> element.lookupString.contains("Extract function", ignoreCase = true) })
    }

    fun testExtractMethodInChainCallWithPrefix() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            class A {
                fun f() : String = ""
                
                fun f() {
                    A().<caret>.f()
                }
            """
        )

        val elements = myFixture.completeBasic()
        assertTrue(elements.any { element -> element.lookupString.contains("Extract function", ignoreCase = true) })
    }

    fun testExtractMethodInControlFlowForAfterLBrace() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun foo() {
                for (i in 1..10) {.<caret>
                    println(i)
                }
            }
        """.trimIndent())
        val elements = myFixture.completeBasic()
        assertTrue(elements.any { element -> element.lookupString.contains("Extract function", ignoreCase = true) })
    }

    fun testExtractMethodInControlFlowForAfterRBrace() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun foo() {
                for (i in 1..10) {
                }.<caret>
            }
            """
        )
        val elements = myFixture.completeBasic()
        assertTrue(elements.any { element -> element.lookupString.contains("Extract function", ignoreCase = true) })
    }

    fun testExtractMethodInControlFlowIf() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun foo() {
                if(true) {.<caret>
                }
            }
            """
        )
        val elements = myFixture.completeBasic()
        assertTrue(elements.any { element -> element.lookupString.contains("Extract function", ignoreCase = true) })
    }

    fun testExtractMethodInControlFlowIfElse() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun foo() {
                if(true) {
                } else {.<caret>
                }
            }
            """
        )
        val elements = myFixture.completeBasic()
        assertTrue(elements.any { element -> element.lookupString.contains("Extract function", ignoreCase = true) })
    }

    fun testExtractMethodInControlFlowIfElseIf() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun foo() {
                if(true) {
                } else if (true) {
                } else if (true) {.<caret>
                } else {
                }
            }
            """
        )
    }

    fun testInlineMethod() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun foo() {
            
                val a = "1"
            }
            
            fun bar(){
                foo().<caret>
            }
            """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(myFixture, elements.first { element -> element.lookupString.contains("Inline", ignoreCase = true) })
        myFixture.checkResult(
            """
            fun bar() {
                val a = "1"
            }""".trimIndent()
        )
    }

    fun testInlineMethodOnIdentifier() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun foo.<caret>() {
            
                val a = "1"
            }
            
            fun bar(){
                foo()
            }
            """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(myFixture, elements.first { element -> element.lookupString.contains("Inline", ignoreCase = true) })
        myFixture.checkResult(
            """
            fun bar() {
                val a = "1"
            }""".trimIndent()
        )
    }

    fun testInlineMethodOnRBracket() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun foo() {
            
                val a = "1"
            }.<caret>
            
            fun bar(){
                foo()
            }
            """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(myFixture, elements.first { element -> element.lookupString.contains("Inline", ignoreCase = true) })
        myFixture.checkResult(
            """
            fun bar() {
                val a = "1"
            }""".trimIndent()
        )
    }

    fun testInlineMethodExpression() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun foo() = 1.<caret>
            
            fun bar(): Int {
                return foo()
            }
            """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        selectItem(myFixture, elements.first { element -> element.lookupString.contains("Inline", ignoreCase = true) })
        myFixture.checkResult(
            """
            fun bar(): Int {
                return 1
            }""".trimIndent()
        )
    }

    fun testMoveMethod() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            KotlinFileType.INSTANCE, """
      class Main {
      
          fun a(a2: String): String {
              System.out.println(a2)
              return "1"
          }.<caret>
      
      }
      """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertTrue(elements.any { element -> element.lookupString.equals("Move", ignoreCase = true) })
    }

    fun testCopyClass() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            KotlinFileType.INSTANCE, """
      public class Main.<caret> {
      }
      """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertTrue(elements.any { element -> element.lookupString.equals("Copy", ignoreCase = true) })
    }

    fun testCreateFromUsages() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
                fun main() {
                
                    val a = A()
                    a<caret>
                }
                
                class A{}
            """.trimIndent()
        )
        myFixture.type(".aaaa")
        val elements = myFixture.completeBasic()
        TemplateManagerImpl.setTemplateTesting(myFixture.testRootDisposable)
        selectItem(myFixture, elements.first { element -> element.lookupString.contains("Create method", ignoreCase = true) })
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()

        myFixture.checkResult(
            """
            fun main() {
            
                val a = A()
                a.aaaa()
            }
            
            class A{
                fun aaaa() {
                    TODO("Not yet implemented")
                }
            }
        """.trimIndent()
        )
    }

    fun testNoCreateFromUsagesAfterDoubleDot() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            KotlinFileType.INSTANCE, """
        enum class Color {
          RED, GREEN, BLUE, YELLOW, BROWN
        }

        class A {
          val color = Color.BROWN..<caret>
        }
      """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertNull(elements.firstOrNull { element -> element.lookupString.contains("Create method from", ignoreCase = true) })
    }

    fun testCreateFromUsagesBeforeSemiComa() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            KotlinFileType.INSTANCE, """
        enum class Color {
          RED, GREEN, BLUE, YELLOW, BROWN
        }

        class A {
          val color = Color.BROWN.<caret>;
        }
      """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertTrue(elements.any { element -> element.lookupString.contains("Create method from", ignoreCase = true) })
    }

    fun testFirstCompletion() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun foo() : String {
                return "1"
            }
            
            fun bar(){
                foo()..<caret>
            }
            """.trimIndent()
        )
        val elements = myFixture.complete(CompletionType.BASIC, 0)
        assertTrue(elements[0].`as`(CommandCompletionLookupElement::class.java) != null)
    }

    fun testNoCompletionInsideStringBlock() {
      Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
      myFixture.configureByText(
        "x.kt", """
            class A {
                fun test() {
                    call(
                        ""${'"'}some.a.<caret>
                    ""${'"'}.trimMargin(), "1"
                    )
                }
            }
            
            fun call(key: String, key2: String) {
                println(key + key2)
            }""".trimIndent()
      )
      val elements = myFixture.complete(CompletionType.BASIC, 0)
      assertTrue(elements.none { it.`as`(CommandCompletionLookupElement::class.java) != null })
    }

    fun testNoCompletionInsideStringLiteral() {
      Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
      myFixture.configureByText(
        "x.kt", """
            class A {
                fun test3() {
                   call("some.a.<caret>", "1")
                }
            }
            
            fun call(key: String, key2: String) {
                println(key + key2)
            }""".trimIndent()
      )
      val elements = myFixture.complete(CompletionType.BASIC, 0)
      assertTrue(elements.none { it.`as`(CommandCompletionLookupElement::class.java) != null })
    }

    fun testNoCompletionInsideStringInsideInterpolation() {
      Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
      myFixture.configureByText(
        "x.kt", """
            class A {
                fun test2(a: String) {
                    call(
                        "some.a.${'$'}{a.<caret>}".trimMargin(), "1"
                    )
                }
            }
            
            fun call(key: String, key2: String) {
                println(key + key2)
            }""".trimIndent()
      )
      val elements = myFixture.complete(CompletionType.BASIC, 0)
      assertFalse(elements.none { it.`as`(CommandCompletionLookupElement::class.java) != null })
    }

    fun testNotFirstCompletion() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
            fun foo() : Int {
                return 1
            }
            
            fun bar(){
                foo()..<caret>
            }
            """.trimIndent()
        )
        val elements = myFixture.complete(CompletionType.BASIC, 0)
        assertFalse(elements[0].`as`(CommandCompletionLookupElement::class.java) != null)
    }

    fun testOptimizeImport() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """"
          import java.util.List.<caret>
          
          class A {
          }""".trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertTrue(elements.any { element -> element.lookupString.contains("Optimize im", ignoreCase = true) })
    }

    fun testParameterInfo() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """"
          class A{
            fun call(a: String){}
            
            fun test(){
              call(.<caret>)
            }
          }
          """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertTrue(elements.any { element -> element.lookupString.contains("Parameter info", ignoreCase = true) })
    }
}

internal fun selectItem(fixture: JavaCodeInsightTestFixture,
                        item: LookupElement,
                        completionChar: Char = 0.toChar()) {
    val lookup: LookupImpl = fixture.lookup as LookupImpl
    lookup.setCurrentItem(item)
    if (LookupEvent.isSpecialCompletionChar(completionChar)) {
        lookup.finishLookup(completionChar)
    } else {
        fixture.type(completionChar)
    }
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
}
