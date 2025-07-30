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
        selectItem(elements.first { element -> element.lookupString.contains("Change type of", ignoreCase = true) })
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
        selectItem(elements.first { element -> element.lookupString.equals("Reformat code", ignoreCase = true) })
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
        selectItem(elements.first { element -> element.lookupString.contains("Comment with line", ignoreCase = true) })
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
        selectItem(elements.first { element -> element.lookupString.contains("Comment with block", ignoreCase = true) })
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
        selectItem(elements.first { element -> element.lookupString.contains("Delete", ignoreCase = true) })
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
        selectItem(elements.first { element -> element.lookupString.contains("Generate 'Secondary", ignoreCase = true) })
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
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
        myFixture.checkResult(
            """
            open class TestSuper {
                open fun <caret>foo() {}
            
                class Child : TestSuper() {
                    override fun foo() {
                        super.foo()
                        println()
                    }
                }
            }""".trimIndent()
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
      """.trimIndent())
        val elements = myFixture.completeBasic()
        selectItem(elements.first { element -> element.lookupString.contains("Go to impl", ignoreCase = true) })
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        myFixture.checkResult("""
        interface A{
        
            fun a()
        
            class B : A{
        
                override fun <caret>a() {
        
                }
            }
        }
      """.trimIndent())
    }

    fun testCommandsOnlyGoToImplementationNotFound() {
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
        myFixture.configureByText(
            "x.kt", """
        interface A{
            fun a..<caret>()
        }
      """.trimIndent())
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
        selectItem(elements.first { element -> element.lookupString.contains("Introduce parameter", ignoreCase = true) })
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
        myFixture.checkResult(
            """
            fun foo(string: String) {
            
                val a = string
            }""".trimIndent()
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
        selectItem(elements.first { element -> element.lookupString.contains("Inline", ignoreCase = true) })
        myFixture.checkResult(
            """
            fun bar() {
                val a = "1"
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
        selectItem(elements.first { element -> element.lookupString.contains("Create method", ignoreCase = true) })
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