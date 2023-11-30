// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSTryCatchExpressionTest : KotlinStructuralSearchTest() {
    fun testTryCatch() {
        doTest(
            """
            try {
                println(0)
            } catch (e: Exception) {
                println(1)
            }
            """, """
            fun a() {
                <warning descr="SSR">try {
                    println(0)
                } catch (e: Exception) {
                    println(1)
                }</warning>

                try {
                    println(0)
                } catch (e: Exception) {
                    println(2)
                }

                try {
                    println(1)
                } catch (e: Exception) {
                    println(1)
                }

                try {
                    println(0)
                } catch (e: IllegalStateException) {
                    println(1)
                }
            }
        """.trimIndent()
        )
    }

    fun testTryFinally() {
        doTest(
            """
            try {
                println(0)
            } finally {
                println(1)
            }
            """, """
                fun a() {
                    <warning descr="SSR">try {
                        println(0)
                    } finally {
                        println(1)
                    }</warning>

                    try {
                        println(0)
                    } finally {
                        println(2)
                    }

                    try {
                        println(1)
                    } finally {
                        println(1)
                    }
                }
            """.trimIndent()
        )
    }
}