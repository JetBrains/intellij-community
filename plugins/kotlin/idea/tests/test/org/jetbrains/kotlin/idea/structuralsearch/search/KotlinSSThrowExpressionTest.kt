// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

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