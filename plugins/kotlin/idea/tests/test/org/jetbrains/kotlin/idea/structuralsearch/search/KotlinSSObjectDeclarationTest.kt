// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSResourceInspectionTest
import org.jetbrains.kotlin.idea.structuralsearch.filters.AlsoMatchCompanionObjectModifier
import org.jetbrains.kotlin.idea.structuralsearch.filters.OneStateFilter

class KotlinSSObjectDeclarationTest : KotlinSSResourceInspectionTest() {
    fun testObject() {
        doTest("object '_", """
            <warning descr="SSR">object A { }</warning>
            class B { 
                companion object { }
            }
            fun main() {
                val x = object { }
                println(x)
            }            
        """.trimIndent())
    }

    fun testNestedObject() {
        doTest("object B", """
            object A {
                <warning descr="SSR">object B { }</warning>
            }
            class C {
               companion object B { }
           }           
        """.trimIndent())
    }

    fun testObjectAlsoMatchCompanionObject() {
        doTest("object '_:[_${AlsoMatchCompanionObjectModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})]", """
           class A {
                companion object { }
            }
            class B {
                <warning descr="SSR">companion object Factory { }</warning>
            }
            <warning descr="SSR">object C { }</warning>
    """.trimIndent())
    }

    fun testObjectCountModifier() {
        doTest("object '_{0,1}", """
           class A {
                companion object { }
            }
            class B {
                companion object Factory { }
            }
            <warning descr="SSR">object C { }</warning>
            fun main() {
                val x = <warning descr="SSR">object { }</warning>
                println(x)
            }
    """.trimIndent())
    }

    fun testObjectAlsoMatchCompanionObjectCountModifier() {
        doTest("object '_{0,1}:[_${AlsoMatchCompanionObjectModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})]", """
           class A {
                <warning descr="SSR">companion object { }</warning>
            }
            class B {
                <warning descr="SSR">companion object Factory { }</warning>
            }
            <warning descr="SSR">object C { }</warning>
    """.trimIndent())
    }

    fun testCompanionObject() { doTest("""
        class '_ {
            companion object '_ { }
        }
    """.trimIndent(), """
       class A {
            companion object { }
        }
        <warning descr="SSR">class B {
            companion object Factory { }
        }</warning>
        object C { }
    """.trimIndent())
    }

    fun testNamelessCompanionObject() { doTest("""
        class '_ {
            companion object { }
        }
    """.trimIndent(), """
        <warning descr="SSR">class A {
            companion object { }
        }</warning>
        class B {
            companion object Factory { }
        }
        object C { }
    """.trimIndent())
    }

    fun testCompanionObjectCountModifier() { doTest("""
        class '_ {
            companion object '_{0,1} { }
        }
    """.trimIndent(), """
       <warning descr="SSR">class A {
            companion object { }
        }</warning>
        <warning descr="SSR">class B {
            companion object Factory { }
        }</warning>
        object C { }
    """.trimIndent())
    }

    fun testNamedCompanionObject() {
        doTest("""
            class '_ {
                companion object Foo { }
            }
        """.trimIndent(), """
            <warning descr="SSR">class A {
                companion object Foo { }
            }</warning>
        """.trimIndent()
        )
    }

    fun testNestedNamedCompanionObject() {
        doTest("""
            class '_ {
                companion object Foo { }
            }
            """.trimIndent(), """
                <warning descr="SSR">class ContainerForCompanion {
                    companion object Foo { }
                }</warning>
            """.trimIndent()
        )
    }
}