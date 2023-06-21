// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSExpressionWithLabelTest : KotlinStructuralSearchTest() {
    fun testBreak() { doTest("break", """
        fun main() {
            for (i in 0..1) {
                <warning descr="SSR">break</warning>
            }
        }
    """.trimIndent()) }

    fun testBreakLabel() { doTest("break@loop", """
        fun main() {
            loop@ for (i in 1..100) {
                for (j in 1..100) {
                    <warning descr="SSR">break@loop</warning>
                }
            }
        }
    """.trimIndent()) }

    fun testContinue() { doTest("continue", """
        fun main() {
            for (i in 0..1) {
                <warning descr="SSR">continue</warning>
            }
        }
    """.trimIndent()) }

    fun testContinueLabel() { doTest("continue@loop", """
        fun main() {
            loop@ for (i in 1..100) {
                for (j in 1..100) {
                    <warning descr="SSR">continue@loop</warning>
                }
            }
        }
    """.trimIndent()) }

    fun testContinueLabelRegex() { doTest("continue@'_foo:[regex( foo.* )]", """
        fun main() {
            foo@ for (i in 1..100) {
                for (j in 1..100) {
                    <warning descr="SSR">continue@foo</warning>
                }
            }
            foo1@ for (i in 1..100) {
                for (j in 1..100) {
                    <warning descr="SSR">continue@foo1</warning>
                }
            }
            bar@ for (i in 1..100) {
                for (j in 1..100) {
                    continue@bar
                }
            }
        }
    """.trimIndent()) }

    fun testReturn() { doTest("return 1", """
        fun main(): Int {
            <warning descr="SSR">return 1</warning>
        }
    """.trimIndent()) }

    fun testReturnLabel() { doTest("return@lit", """
        fun foo() {
            listOf(1, 2, 3, 4, 5).forEach lit@{
                if (it == 3) <warning descr="SSR">return@lit</warning>
            }
        }
    """.trimIndent()) }

    fun testSuper() { doTest("super", """
        class A {
            val a = <warning descr="SSR">super</warning>.hashCode()
        }
    """.trimIndent()) }

    fun testSuperTypeQualifier() { doTest("super<B>", """
        interface B {
            fun foo() {}
        }

        interface C {
            fun foo() {}
        }

        class A : B, C {
            override fun foo() {
                <warning descr="SSR">super<B></warning>.foo()
                super<C>.foo()
            }
        }

        fun main() { A().foo() }
    """.trimIndent()) }

    fun testThis() { doTest("this", """
        class A {
            val a = <warning descr="SSR">this</warning>
        }
    """.trimIndent()) }

    fun testThisLabel() { doTest("this@A", """
        class A {
            inner class B {
                fun foo() {
                    println(<warning descr="SSR">this@A</warning>)
                    println(this@B)
                }
            }
        }
    """.trimIndent()) }
}