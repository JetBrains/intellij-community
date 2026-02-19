// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.search

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchTest

class KotlinSSThrowExpressionTest : KotlinStructuralSearchTest() {
    fun testAnyException() { doTest("throw '_", """
        fun fooOne() {
            <warning descr="SSR">throw Exception()</warning>
        }

        fun fooTwo() {
            println()
        }
    """.trimIndent()) }
}