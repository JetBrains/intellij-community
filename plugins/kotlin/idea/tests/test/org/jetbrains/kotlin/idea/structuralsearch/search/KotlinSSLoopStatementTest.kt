// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSLoopStatementTest : KotlinStructuralSearchTest() {
    fun testForLoop() {
        doTest(
            """
            for(i in 0..10) {
                println(i)
            }
            """, """
            fun a() {
                <warning descr="SSR">for(i in 0..10) {
                    println(i)
                }</warning>

                for(i in 0..9) {
                    println(i)
                }

                for(j in 0..10) {
                    println(j)
                }

                <warning descr="SSR">for(i in 0..10) println(i)</warning>
            }
        """.trimIndent()
        )
    }

    fun testWhileLoop() {
        doTest(
            """
            while(true) {
                println(0)
            }
            """, """
            fun a() {
                <warning descr="SSR">while(true) {
                    println(0)
                }</warning>
            }

            fun b() {
                while(false) {
                    println(0)
                }
            }

            fun c() {
                while(true) {
                    println(1)
                }
            }

            fun d() {
                <warning descr="SSR">while(true) println(0)</warning>
            }
        """.trimIndent()
        )
    }

    fun testDoWhileLoop() {
        doTest(
            """
            do {
                println(0)
            } while(true)
            """, """
            fun a() {
                <warning descr="SSR">do {
                    println(0)
                } while(true)</warning>
            }

            fun b() {
                 do {
                    println(0)
                } while(false)
            }

            fun c() {
                do {
                    println(1)
                } while(true)
            }

            fun d() {
                <warning descr="SSR">do println(0) while(true)</warning>
            }
        """.trimIndent()
        )
    }

    fun testWhileTwoStatements() {
        doTest(
            "while ('_) { '_{2,2} }", """
        fun foo() {
            var x = true

            while (x)
                println()

            while (x) {
                println()
            }

            <warning descr="SSR">while (x) {
                println()
                println()
            }</warning>

        }
    """.trimIndent()
        )
    }
}