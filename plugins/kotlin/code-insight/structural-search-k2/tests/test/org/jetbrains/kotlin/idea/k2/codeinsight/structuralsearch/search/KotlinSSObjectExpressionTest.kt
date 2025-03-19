// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.search

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchTest

class KotlinSSObjectExpressionTest : KotlinStructuralSearchTest() {
    fun testObject() {
        doTest(pattern = """
            fun '_() = object {
                val c = 1
            }
        """.trimIndent(), highlighting = """
            <warning descr="SSR">fun a() = object {
                val c = 1
            }</warning>
            class A() {
                <warning descr="SSR">private fun b() = object {
                    val c = 1
                }</warning>
                <warning descr="SSR">fun c() = object {
                    val c = 1
                }</warning>
            }
        """.trimIndent()
        )
    }

    fun testObjectAnonymous() {
        doTest(pattern = """
            private fun '_() = object {
                val c = 1
            }
        """, highlighting = """
            fun a() = object {
                val c = 1
            }
            class A() {
                <warning descr="SSR">private fun b() = object {
                    val c = 1
                }</warning>
                fun c() = object {
                    val c = 1
                }
            }
        """.trimIndent()
        )
    }

    fun testObjectSuperType() {
        doTest(pattern = """
            fun '_() = object : '_() { }
        """, highlighting = """
            open class X
            <warning descr="SSR">fun a() = object : X() { }</warning>
            fun b() = object { }
        """.trimIndent()
        )
    }
}