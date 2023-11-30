// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.search

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchTest

class KotlinSSLambdaExpressionTest : KotlinStructuralSearchTest() {
    fun testEmpty() { doTest("{}", """
        var foo = 2.also <warning descr="SSR">{}</warning>
        val bar1 = 1.also { it.inc() }
        val bar2 = 1.also { it -> it.inc() }
    """.trimIndent()) }

    fun testBody() { doTest("{ '_Expr+ }", """
        var foo = 2.also{}
        val bar1 = 1.also <warning descr="SSR">{ it.inc() }</warning>
        val bar2 = 1.also <warning descr="SSR">{ it -> it.inc() }</warning>
    """.trimIndent()) }

    fun testExplicitIt() { doTest("{ it -> '_Expr+ }", """
        var foo = 2.also {}
        val bar1 = 1.also { it.inc() }
        val bar2 = 1.also <warning descr="SSR">{ it -> it.inc() }</warning>
    """.trimIndent()) }

    fun testIdentity() { doTest("{ '_x -> '_x }", """
        val foo: (Int) -> Int = <warning descr="SSR">{ it -> it }</warning>
        val foo2: (Int) -> Int = { 1 }
        var bar1: (Int) -> Unit = {}
        val bar2: () -> Int = { 1 }
        val bar3: (Int) -> Int = { it -> 1 }
    """.trimIndent()) }

    fun testAnnotated() { doTest("@Ann { println() }", """
        @Target(AnnotationTarget.FUNCTION)
        annotation class Ann

        fun annotatedLambda() = <warning descr="SSR">@Ann { println() }</warning>
        fun notAnnotatedLambda() = { println() }
    """.trimIndent()) }
}