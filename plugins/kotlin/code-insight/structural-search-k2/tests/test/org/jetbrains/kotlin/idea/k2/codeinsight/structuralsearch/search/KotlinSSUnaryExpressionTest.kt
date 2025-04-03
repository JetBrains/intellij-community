// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.search

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchTest

class KotlinSSUnaryExpressionTest : KotlinStructuralSearchTest() {
    fun testUnaryPlus() { doTest("+3", """
        val a = <warning descr="SSR">+3</warning>+3
        val b = <warning descr="SSR">3.unaryPlus()</warning>
        val c = -3
        val d = 3.unaryMinus()
        val e = 3 +3
    """.trimIndent()) }

    fun testUnaryMinus() { doTest("-3", """
        val a = <warning descr="SSR">-3</warning>+3
        val b = <warning descr="SSR">3.unaryMinus()</warning>
        val c = +3
        val d = 3.unaryPlus()
        val e = 3 -3
    """.trimIndent()) }

    fun testNot() { doTest("!'_", """
        val a = true
        val b = <warning descr="SSR">!a</warning>
        val c = <warning descr="SSR">a.not()</warning>
    """.trimIndent()) }

    fun testPreIncrement() { doTest("++'_ ", """
        fun postIncrement(a: Int): Int {
            var b = a
            <warning descr="SSR">++b</warning>
            <warning descr="SSR">++<warning descr="[WRAPPED_LHS_IN_ASSIGNMENT_WARNING] Wrapping the left-hand side of assignments in parentheses, labels or annotations is not allowed. This will become an error in language version 2.2.">(b)</warning></warning>
            <warning descr="SSR">++<warning descr="[WRAPPED_LHS_IN_ASSIGNMENT_WARNING] Wrapping the left-hand side of assignments in parentheses, labels or annotations is not allowed. This will become an error in language version 2.2.">(((b)))</warning></warning>
            <warning descr="SSR">b.inc()</warning>
            return b
        }
    """.trimIndent()) }

    fun testPostIncrement() { doTest("'_ ++", """
        fun postIncrement(a: Int): Int {
            var b = a
            <warning descr="SSR">b++</warning>
            <warning descr="SSR"><warning descr="[WRAPPED_LHS_IN_ASSIGNMENT_WARNING] Wrapping the left-hand side of assignments in parentheses, labels or annotations is not allowed. This will become an error in language version 2.2.">(b)</warning>++</warning>
            <warning descr="SSR"><warning descr="[WRAPPED_LHS_IN_ASSIGNMENT_WARNING] Wrapping the left-hand side of assignments in parentheses, labels or annotations is not allowed. This will become an error in language version 2.2.">(((b)))</warning>++</warning>
            <warning descr="SSR">b.inc()</warning>
            return b
        }
    """.trimIndent()) }

    fun testPreDecrement() { doTest("--'_", """
        fun postDecrement(a: Int): Int {
            var b = a
            <warning descr="SSR">--b</warning>
            <warning descr="SSR">b.dec()</warning>
            return b
        }
    """.trimIndent()) }

    fun testPostDecrement() { doTest("'_--", """
        fun postDecrement(a: Int): Int {
            var b = a
            <warning descr="SSR">b--</warning>
            <warning descr="SSR">b.dec()</warning>
            return b
        }
    """.trimIndent()) }

    fun testAssertNotNull() { doTest("'_!!", """
        fun main() {
            val a: Int? = 1
            print(<warning descr="SSR">a!!</warning>)
        }
    """.trimIndent()) }
}