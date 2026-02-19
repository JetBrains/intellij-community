// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSDoubleColonExpressionTest : KotlinStructuralSearchTest() {
    fun testClassLiteralExpression() { doTest("Int::class", """
        fun foo(x: Any) { print(x) }

        class X { class Y { class Z { class Int } } }

        fun main() {
            foo(<warning descr="SSR">Int::class</warning>)
            foo(<warning descr="SSR">kotlin.Int::class</warning>)
            foo(<warning descr="SSR">X.Y.Z.Int::class</warning>)
            foo(<warning descr="SSR">Int::class</warning>.java)
        }
    """.trimIndent()) }

    fun testFqClassLiteralExpression() { doTest("kotlin.Int::class", """
        fun foo(x: Any) { print(x) }

        class X { class Y { class Z { class Int } } }

        fun main() {
            foo(Int::class)
            foo(<warning descr="SSR">kotlin.Int::class</warning>)
            foo(X.Y.Z.Int::class)
            foo(Int::class.java)
        }
    """.trimIndent()) }

    fun testDotQualifiedExpression() { doTest("Int::class.java", """
        fun foo(x: Any) { print(x) }

        fun main() {
            foo(Int::class)
            foo(<warning descr="SSR">Int::class.java</warning>)
        }
    """.trimIndent()) }
}