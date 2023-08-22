// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSBinaryWithTypeRHSExpressionTest : KotlinStructuralSearchTest() {
    fun testAs() { doTest("'_ as '_", """
        fun foo(x: Int): Any = when(x) {
            0 -> 1
            else -> "1"
        }

        val foo1 = <warning descr="SSR">foo(0) as Int</warning>
        val foo2 = <warning descr="SSR">foo(1) as String</warning>
        val bar1 = foo(1) as? String
        val bar2 = foo(1) is String
    """.trimIndent()) }

    fun testAsSafe() { doTest("'_ as? '_", """
        fun foo(x: Int): Any = when(x) {
            0 -> 1
            else -> "1"
        }

        val foo1 = foo(0) as Int
        val foo2 = foo(1) as String
        val bar1 = <warning descr="SSR">foo(1) as? String</warning>
        val bar2 = foo(1) is String
    """.trimIndent()) }
}