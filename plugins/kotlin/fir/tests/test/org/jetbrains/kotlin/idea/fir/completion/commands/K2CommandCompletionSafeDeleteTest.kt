// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.completion.commands

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class K2CommandCompletionSafeDeleteTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode = KotlinPluginMode.K2

    override fun setUp() {
        super.setUp()
        Registry.get("ide.completion.command.enabled").setValue(false, getTestRootDisposable())
        Registry.get("ide.completion.command.force.enabled").setValue(true, getTestRootDisposable())
    }

    fun testClassIdentifier() {
        myFixture.configureByText(
            "x.kt", """
        class A.<caret> { 
            fun foo(a: String) {
                var b = a
            } 
        }
        
        class B{
            fun bar(){
                var c = A()
            }
        }
      """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertNotNull(elements.firstOrNull() { element -> element.lookupString.contains("Safe delete", ignoreCase = true) })
    }

    fun testClassEnd() {
        myFixture.configureByText(
            "x.kt", """
        class A { 
            fun foo(a: String) {
                var b = a
            } 
        }.<caret>
        
        
        class B{
            fun bar(){
                var c = A()
            }
        }
      """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertNotNull(elements.firstOrNull() { element -> element.lookupString.contains("Safe delete", ignoreCase = true) })
    }

    fun testClassReference() {
        myFixture.configureByText(
            "x.kt", """
        class A { 
            fun foo(a: String) {
                var b = a
            } 
        }
        
        
        class B{
            fun bar(){
                var c = A.<caret>()
            }
        }
      """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertNotNull(elements.firstOrNull() { element -> element.lookupString.contains("Safe delete", ignoreCase = true) })
    }


    fun testMethodIdentifier() {
        myFixture.configureByText(
            "x.kt", """
        class A { 
            fun foo.<caret>(a: String) {
                var b = a
            } 
        }
        
        class B{
            fun bar(){
                var c = A().foo("1")
            }
        }
      """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertNotNull(elements.firstOrNull() { element -> element.lookupString.contains("Safe delete", ignoreCase = true) })
    }

    fun testMethodEnd() {
        myFixture.configureByText(
            "x.kt", """
        class A { 
            fun foo(a: String) {
                var b = a
            } .<caret>
        }
        
        
        class B{
            fun bar(){
                var c = A().foo("1")
            }
        }
      """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertNotNull(elements.firstOrNull() { element -> element.lookupString.contains("Safe delete", ignoreCase = true) })
    }


    fun testMethodReference() {
        myFixture.configureByText(
            "x.kt", """
                
        class A { 
            fun foo(a: String) {
                var b = a
            } 
        }
        
        
        class B{
            fun bar(){
                var c = A().foo.<caret>("1")
            }
        }
      """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertNotNull(elements.firstOrNull() { element -> element.lookupString.contains("Safe delete", ignoreCase = true) })
    }

    fun testVariable() {
        myFixture.configureByText(
            "x.kt", """
                
        class A { 
            fun foo(a.<caret>: String) {
                var b = a
            } 
        }
        
        
        class B{
            fun bar(){
                var c = A().foo("1")
            }
        }
      """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Safe delete", ignoreCase = true) })
    }
    fun testVariableReference() {
        myFixture.configureByText(
            "x.kt", """
                
        class A { 
            fun foo(a: String) {
                var b = a.<caret>
            } 
        }
        
        
        class B{
            fun bar(){
                var c = A().foo("1")
            }
        }
      """.trimIndent()
        )
        val elements = myFixture.completeBasic()
        assertNotNull(elements.firstOrNull { element -> element.lookupString.contains("Safe delete", ignoreCase = true) })
    }
}