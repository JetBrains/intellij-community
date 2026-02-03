// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.search

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchTest

class KotlinSSIfExpressionTest : KotlinStructuralSearchTest() {
    fun testIf() { doTest("if(true) b = true", """
        fun a(): Boolean {
            var b = false
            <warning descr="SSR">if(true) b = true</warning>
            <warning descr="SSR">if(true) {
                b = true
            }</warning>
            return b
        }
    """.trimIndent()) }

    fun testIfElse() { doTest("if(true) 1 else 2", """
        fun a(): Int {
            return <warning descr="SSR">if(true) 1 else 2</warning>
        }
    """.trimIndent()) }

    fun testIfBlock() {
        doTest(
            """
            if(true) {
|               a = 1
|           }""".trimMargin()
        , """
            fun foo() {
                var a = 0
                var b = 0
                <warning descr="SSR">if (true) {
                    a = 1
                }</warning>

                if (true) {
                    b = 2
                }
                <warning descr="SSR">if (true) a = 1</warning>
                println(a + b)
            }
        """.trimIndent())
    }

    fun testIfElseBlock() {
        doTest(
            """
            if (a == 1) {
                a = 2
            } else {
                a = 3
            }""".trimMargin()
        , """
            fun a(): Int {
                var a = 1
                <warning descr="SSR">if (a == 1) {
                    a = 2
                } else {
                    a = 3
                }</warning>

                if (a == 1) {
                    a = 2
                } else {
                    a = 4
                }

                <warning descr="SSR">if (a == 1) a = 2 else a = 3</warning>
                return a
            }
        """.trimIndent())
    }

    fun testIfElseCondition() {
        doTest(
            """
            if (a == 1) {
                a = 2
            } else {
                a = 3
            }""".trimMargin()
        , """
            fun a(): Int {
                var a = 1
                <warning descr="SSR">if (a == 1) {
                    a = 2
                } else {
                    a = 3
                }</warning>

                if (a == 0) {
                    a = 2
                } else {
                    a = 3
                }
                return a
            }
        """.trimIndent())
    }

    fun testIfThen1Expr() {
        doTest("if ('_) { '_{1,1} }", """
            fun a() {
                var b = false
                <warning descr="SSR">if (true) b = true</warning>
                <warning descr="SSR">if (true) {
                    b = true
                }</warning>
                if (true) {
                    b = true
                    print(b)
                }
                print(b)
            }
        """.trimIndent())
    }
}