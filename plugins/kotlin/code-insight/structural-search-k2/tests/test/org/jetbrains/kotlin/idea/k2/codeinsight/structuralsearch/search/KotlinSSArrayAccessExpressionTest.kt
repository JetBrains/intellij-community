// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.search

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchTest

class KotlinSSArrayAccessExpressionTest : KotlinStructuralSearchTest() {
    fun testConstAccess() { doTest("a[0]", """
        val a = arrayOf(0, 1)
        val b = <warning descr="SSR">a[0]</warning>
        val c = <warning descr="SSR">a[(0)]</warning>
        val d = <warning descr="SSR">(a)[0]</warning>
        val e = <warning descr="SSR">(a)[(0)]</warning>
        val f = <warning descr="SSR">(((a)))[(((0)))]</warning>
        val g = a[1]
    """.trimIndent()) }

    fun testConstAccessGet() { doTest("a[0]", """
        val a = arrayOf(0, 1)

        val b = <warning descr="SSR">a[0]</warning>

        val c = <warning descr="SSR">a.get(0)</warning>

        val d = a.get(1)

        val e = arrayOf(1, 1)

        val f = e.get(0)

        val g = a.set(0, 0)
    """.trimIndent()) }
}