// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSWhenExpressionTest : KotlinStructuralSearchTest() {
    override fun getBasePath(): String = "whenExpression"

    fun testWhenVariableSubject() {
        doTest(
            """
            when(b) {
                true -> b = false
                false -> b = true
            }
            """
        )
    }

    fun testWhenExpressionSubject() {
        doTest(
            """
            when(val b = false) {
                true -> println(b)
                false ->  println(b)
            }
            """
        )
    }

    fun testWhenRangeEntry() {
        doTest(
            """
            when(10) {
                in 3..10 -> '_
                else -> '_
            }
            """
        )
    }

    fun testWhenExpressionEntry() {
        doTest(
            """
            when {
                a == b -> return true
                else ->  return false
            }
            """
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
            """
        )
    }

    fun testWhenVarRefEntry() {
        doTest(
            """
            when (i) {
                '_ -> '_
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
            """.trimIndent()
        )
    }

    fun testWhenElseEntry() {
        doTest(
            """
            when ('_?) {
                else -> println()
            }
            """
        )
    }

    fun testWhenNoMatch() {
        doTest(
            """
            when('_a) {
                '_a -> '_b
            }
            """.trimIndent()
        )
    }
}