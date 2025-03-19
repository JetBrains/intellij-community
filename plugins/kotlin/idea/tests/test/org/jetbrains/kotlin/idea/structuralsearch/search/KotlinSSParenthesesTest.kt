// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSParenthesesTest : KotlinStructuralSearchTest() {
    fun testPostIncr() { doTest("++(('_))", """
        fun postIncrement(a: Int): Int {
            var b = a
            ++b
            ++(b)
            <warning descr="SSR">++((b))</warning>
            ++(((b)))
            return b
        }
    """.trimIndent()) }

    fun testStringLiteral() { doTest("(((\"Hello World!\")))", """
        val a = "Hello World!"
        val b = ("Hello World!")
        val c = (("Hello World!"))
        val d = <warning descr="SSR">((("Hello World!")))</warning>
    """.trimIndent()) }

    fun testVariableRef() { doTest("(('_))", """
        val a = 3
        val b = (a)
        val c = <warning descr="SSR">((a))</warning>
    """.trimIndent()) }

    fun testBinaryExpr() { doTest("1 + 2 + 3", """
        val a = <warning descr="SSR">1 + 2 + 3</warning>
        val b = <warning descr="SSR">(1 + 2) + 3</warning>
        val c = 1 + (2 + 3)
        val d = <warning descr="SSR">(<warning descr="SSR">1 + 2 + 3</warning>)</warning>
    """.trimIndent()) }
}