// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.search

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchTest

class KotlinSSIsExpressionTest : KotlinStructuralSearchTest() {
    fun testIs() { doTest("'_ is '_", """
        fun foo(arg: Int): Any = when(arg) {
            0 -> 1
            else -> "a"
        }

        val bar1 = <warning descr="SSR">foo(0) is Int</warning>
        val bar2 = foo(1) !is String
    """.trimIndent()) }

    fun testNegatedIs() { doTest("'_ !is '_", """
        fun foo(arg: Int): Any = when(arg) {
            0 -> 1
            else -> "a"
        }

        val bar1 = foo(0) is Int
        val bar2 = <warning descr="SSR">foo(1) !is String</warning>
    """.trimIndent()) }
}