// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.search

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchTest

class KotlinSSWhenExpressionTest : KotlinStructuralSearchTest() {
    fun testWhenVariableSubject() {
        doTest(
            """
            when(b) {
                true -> b = false
                false -> b = true
            }
            """, """
            fun a(): Boolean {
                var b = false
                <warning descr="SSR">when(b) {
                    true -> b = false
                    false -> b = true
                }</warning>
                return b
            }
        """.trimIndent()
        )
    }

    fun testWhenExpressionSubject() {
        doTest(
            """
            when(val b = false) {
                true -> println(b)
                false ->  println(b)
            }
            """, """
            fun a() {
                <warning descr="SSR">when(val b = false) {
                    true -> println(b)
                    false -> println(b)
                }</warning>
            }
        """.trimIndent()
        )
    }

    fun testWhenRangeEntry() {
        doTest(
            """
            when(10) {
                in 3..10 -> '_
                else -> '_
            }
            """, """
            fun a() {
                <warning descr="SSR">when(10) {
                    in 3..10 -> Unit
                    else -> Unit
                }</warning>
            }
        """.trimIndent()
        )
    }

    fun testWhenExpressionEntry() {
        doTest(
            """
            when {
                a == b -> return true
                else ->  return false
            }
            """, """
            fun a(): Boolean {
                val a = 3
                val b = 4
                <warning descr="SSR">when {
                    a == b -> return true
                    else ->  return false
                }</warning>
            }
        """.trimIndent()
        )
    }

    fun testWhenIsPatternEntry() {
        doTest(
            """
            when(a) {
                is Int -> return true
                is String -> return true
                else ->  return false
            }
            """, """
            fun a(): Boolean {
                val a: Any = 3
                <warning descr="SSR">when(a) {
                    is Int -> return true
                    is String -> return true
                    else ->  return false
                }</warning>
            }
        """.trimIndent()
        )
    }

    fun testWhenVarRefEntry() {
        doTest(
            """
            when (i) {
                '_ -> '_
             }
            """.trimIndent(), """
            fun a() {
                val i = 0
                <warning descr="SSR">when (i) {
                    1 -> Unit
                }</warning>
            }
        """.trimIndent()
        )
    }

    fun testWhenAnyEntry() {
        doTest(
            """
            when ('_) {
                '_ -> '_
             }
            """.trimIndent(), """
            fun foo(x: Any): Any = x.hashCode()

            fun a() {
                val i = 0
                <warning descr="SSR">when (foo(i)) {
                    is Int -> Unit
                    else -> Unit
                }</warning>
                <warning descr="SSR">when (i) {
                    1 -> Unit
                }</warning>
            }
        """.trimIndent()
        )
    }

    fun testWhenElseEntry() {
        doTest(
            """
            when ('_?) {
                else -> println()
            }
            """, """
                fun main() {
                    val a = 2
                    val b = 3

                    when (b) {
                        a -> println()
                        in 1..4 -> println()
                    }

                    <warning descr="SSR">when (b) {
                        a -> println()
                        else -> println()
                    }</warning>
                }
            """.trimIndent()
        )
    }

    fun testWhenNoMatch() {
        doTest(
            """
            when('_a) {
                '_a -> '_b
            }
            """.trimIndent(), """
            fun x() {
                val x = 1
                when(x) {
                    4 -> println()
                }
            }
        """.trimIndent()
        )
    }
}