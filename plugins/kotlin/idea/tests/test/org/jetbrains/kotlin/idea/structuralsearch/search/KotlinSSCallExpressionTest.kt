// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest
import org.jetbrains.kotlin.idea.structuralsearch.filters.MatchCallSemanticsModifier
import org.jetbrains.kotlin.idea.structuralsearch.filters.OneStateFilter

class KotlinSSCallExpressionTest : KotlinStructuralSearchTest() {
    fun testConstrArgCall() { doTest("'_:[_${MatchCallSemanticsModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})](true, 0, 1)", """
        class A(val b: Boolean, val c: Int, val d: Int)

        fun b() {
            println(<warning descr="SSR">A(true, 0, 1)</warning>)
            println(A(false, 0, 1))
            println(A(true, 1, 1))
            println(<warning descr="SSR">A(b = true, c = 0, d = 1)</warning>)
            println(<warning descr="SSR">A(c = 0, d = 1, b = true)</warning>)
            println(<warning descr="SSR">A(true, d = 1, c = 0)</warning>)
        }
    """.trimIndent()) }

    fun testConstrCall() { doTest("A()", """
        class A

        class B

        fun b() {
            println(<warning descr="SSR">A()</warning>)
            println(B())
        }
    """.trimIndent()) }

    fun testConstrLambdaArgCall() { doTest("A { println() }", """
        class A(val b: () -> Unit)

        fun c() {
            println(<warning descr="SSR">A { println() }</warning>)
            println(
                A {
                    println()
                    println()
                }
            )
        }
    """.trimIndent()) }

    fun testConstrMixedSpreadVarargCall() { doTest("'_:[_${MatchCallSemanticsModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})](0, 1, 2, 3, 4)", """
        class A(vararg val b: Int)

        fun b(): A {
            return <warning descr="SSR">A(0, *intArrayOf(1, 2, 3), 4)</warning>
        }
    """.trimIndent()) }

    fun testConstrMixedVarargCall() { doTest("'_:[_${MatchCallSemanticsModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})](0, *intArrayOf(1, 2, 3), 4)", """
        class A(vararg val b: Int)

        fun c(): A {
            println(A(0, 1, 2, 3, 4, 5))
            println(A(1, 2, 3, 4, 5))
            println(A(0, 1, 3, 4))
            println(A(0, 2, 3, 4))
            println(A(0, 2, 3, 4))
            println(A(0))
            return <warning descr="SSR">A(0, 1, 2, 3, 4)</warning>
        }
    """.trimIndent()) }

    fun testConstrNamedArgsCall() { doTest("'_:[_${MatchCallSemanticsModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})](b = true, c = 0, d = 1)", """
        class A(val b: Boolean, val c: Int, val d: Int)

        fun d() {
            println(<warning descr="SSR">A(true, 0, 1)</warning>)
            println(<warning descr="SSR">A(b = true, c = 0, d = 1)</warning>)
            println(<warning descr="SSR">A(c = 0, b = true, d = 1)</warning>)
            println(<warning descr="SSR">A(true, d = 1, c = 0)</warning>)
        }
    """.trimIndent()) }

    fun testConstrSpreadVarargCall() { doTest("'_:[regex(A) && _${MatchCallSemanticsModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})](1, 2, 3)", """
        class A(vararg val b: Int)

        fun c(): A {
            return <warning descr="SSR">A(*intArrayOf(1, 2, 3))</warning>
        }
    """.trimIndent()) }

    fun testConstrTypeArgCall() { doTest("A<Int, String>(0, \"a\")", """
        class A<T, R>(val b: T, val c: R)

        fun d(): A<Int, String> {
            return <warning descr="SSR">A<Int, String>(0, "a")</warning>
        }
    """.trimIndent()) }

    fun testConstrVarargCall() { doTest("'_:[_${MatchCallSemanticsModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})](*intArrayOf(1, 2, 3))", """
        class A(vararg val b: Int)

        fun c(): A {
            return <warning descr="SSR">A(1, 2, 3)</warning>
        }
    """.trimIndent()) }

    fun testFunArgCall() { doTest("a(true, 0)", """
        fun a(b: Boolean, c: Int): Boolean {
            return b && c == 0
        }

        fun d(e: Boolean, f: Int): Boolean {
            return e && f == 0
        }

        fun g(): Boolean {
            val h = <warning descr="SSR">a(true, 0)</warning>
            val i = d(true, 0)
            return h && i
        }
    """.trimIndent()) }

    fun testFunArgCallVarRef() { doTest("'_('_)", """
        fun a(): List<Int> {
            val x = <warning descr="SSR">listOf(1)</warning>
            val y = listOf(1, 2)
            return if(x.size == 1) x else y
        }
    """.trimIndent()) }

    fun testFunCall() { doTest("'_()", """
        fun a() { }

        fun b() {
            <warning descr="SSR">a()</warning>
        }
    """.trimIndent()) }

    fun testFunCallDefaultArg() { doTest("'_:[regex(a) && _${MatchCallSemanticsModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})]('_)", """
        fun a(b: Int = 0) { println(b) }

        fun c() {
            <warning descr="SSR">a()</warning>
            <warning descr="SSR">a(0)</warning>
            <warning descr="SSR">a(10)</warning>
        }
    """.trimIndent()) }

    fun testFunCallDefaultArgValue() { doTest("'_:[_${MatchCallSemanticsModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})](0)", """
        fun a(b: Int = 0) { println(b) }

        fun c() {
            <warning descr="SSR">a()</warning>
            <warning descr="SSR">a(0)</warning>
            a(1)
        }
    """.trimIndent()) }

    fun testFunCallDefaultVararg() { doTest("a('_*)", """
        fun a(vararg b: Int = intArrayOf(0)) { println(b) }

        fun c() {
            <warning descr="SSR">a()</warning>
            <warning descr="SSR">a(0)</warning>
            <warning descr="SSR">a(10, 0, 3)</warning>
            <warning descr="SSR">a(*intArrayOf(1, 2, 3))</warning>
            <warning descr="SSR">a(0, *intArrayOf(1, 2, 3), 4)</warning>
        }
    """.trimIndent()) }

    fun testFunCallDefaultArgMixed() { doTest("'_:[_${MatchCallSemanticsModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})]('_{5,5})", """
        fun a(k: Int, b: Int = 0, c: String = "Hello World!", d: Double, vararg t: String): String { return "${"$"}k ${"$"}b ${"$"}c ${"$"}d ${"$"}t" }

        fun c() {
            <warning descr="SSR">a(0, 0, "This is test!", 0.0, "Test")</warning>
            <warning descr="SSR">a(0, d = 0.0, t = *<warning descr="[REDUNDANT_SPREAD_OPERATOR_IN_NAMED_FORM_IN_FUNCTION] Redundant spread (*) operator">arrayOf("Test")</warning>)</warning>
            <warning descr="SSR">a(b = 0, c = "This is a test!", k = 0, t = *<warning descr="[REDUNDANT_SPREAD_OPERATOR_IN_NAMED_FORM_IN_FUNCTION] Redundant spread (*) operator">arrayOf("Test")</warning>, d = 0.0)</warning>
            <warning descr="SSR">a(k = 0, d = 0.0, t = *<warning descr="[REDUNDANT_SPREAD_OPERATOR_IN_NAMED_FORM_IN_FUNCTION] Redundant spread (*) operator">arrayOf("Test")</warning>)</warning>
        }
    """.trimIndent()) }

    fun testFunCallDefaultTrailingLambda() { doTest("'_:[regex(a) && _${MatchCallSemanticsModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})]('_)", """
        fun a(b: (Int) -> Int = { it }) { b(0) }

        fun c() {
            <warning descr="SSR">a()</warning>
            <warning descr="SSR">a() { i -> i }</warning>
            <warning descr="SSR">a { i -> i }</warning>
            <warning descr="SSR">a({ i -> i })</warning>
        }
    """.trimIndent()) }

    fun testFunCallNamedDefaultArg() { doTest("'_:[_${MatchCallSemanticsModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})]('_, '_)", """
        fun a(b: Int, c: Int = 0) { println(b + c) }

        fun d() {
            <warning descr="SSR">a(0)</warning>
            <warning descr="SSR">a(1)</warning>
            <warning descr="SSR">a(1, 0)</warning>
            <warning descr="SSR">a(1, 1)</warning>
        }
    """.trimIndent()) }

    fun testFunExtensionCall() { doTest("0.a()", """
        fun Int.a(): Int {
            return inv()
        }

        fun b(): Int {
            return <warning descr="SSR">0.a()</warning>
        }
    """.trimIndent()) }

    fun testFunLambdaArgCall() { doTest("a { println() }", """
        fun a(b: () -> Unit) {
            b.invoke()
        }

        fun c() {
            <warning descr="SSR">a { println() }</warning>
        }
    """.trimIndent()) }

    fun testFunMixedArgsCall() { doTest("a(c = 0, b = true)", """
        fun a(b: Boolean, c: Int): Boolean {
            return b && c == 0
        }

        fun d(): Boolean {
            return <warning descr="SSR">a(c = 0, b = true)</warning>
        }
    """.trimIndent()) }

    fun testFunMixedSpreadVarargCall() { doTest("'_:[_${MatchCallSemanticsModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})](0, 1, 2, 3, 4)", """
        fun a(vararg b: Int): List<Int> {
            return b.toList()
        }

        fun c(): List<Int> {
            return <warning descr="SSR">a(0, *intArrayOf(1, 2, 3), 4)</warning>
        }
    """.trimIndent()) }

    fun testFunMixedVarargCall() { doTest("'_:[_${MatchCallSemanticsModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})](0, *intArrayOf(1, 2, 3), 4)", """
        fun a(vararg b: Int): List<Int> {
            return b.toList()
        }

        fun c(): List<Int> {
            return <warning descr="SSR">a(0, 1, 2, 3, 4)</warning>
        }
    """.trimIndent()) }

    fun testFunNamedArgsCall() { doTest("a(b = true, c = 0)", """
        fun a(b: Boolean, c: Int): Boolean {
            return b && c == 0
        }

        fun d(): Boolean {
            return <warning descr="SSR">a(b = true, c = 0)</warning>
        }
    """.trimIndent()) }

    fun testFunSpreadVarargCall() { doTest("'_:[regex(a) && _${MatchCallSemanticsModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})](1, 2, 3)", """
        fun a(vararg b: Int): List<Int> {
            return b.toList()
        }

        fun c(): List<Int> {
            return <warning descr="SSR">a(*intArrayOf(1, 2, 3))</warning>
        }
    """.trimIndent()) }

    fun testFunTypeArgCall() { doTest("a<Int, String>(0, \"a\")", """
        fun <T, R> a(b: T, c: R): T? {
            return if(c is String) b else null
        }

        fun d(): Int? {
            return <warning descr="SSR">a<Int, String>(0, "a")</warning>
        }
    """.trimIndent()) }

    fun testFunVarargCall() { doTest("'_:[_${MatchCallSemanticsModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})](*intArrayOf(1, 2, 3))", """
        fun a(vararg b: Int): List<Int> {
            return b.toList()
        }

        fun c(): List<Int> {
            return <warning descr="SSR">a(1, 2, 3)</warning>
        }
    """.trimIndent()) }

    fun testLambdaCallInvoke() { doTest("a()", """
        val a = { }

        fun b() {
            <warning descr="SSR">a()</warning>
            <warning descr="SSR">a.invoke()</warning>
        }

    """.trimIndent()) }

    fun testLambdaCallInvokeArgs() { doTest("a(0, 0)", """
        val a: (Int, Int) -> Unit = { i, j -> }

        fun b() {
            <warning descr="SSR">a(0, 0)</warning>
            <warning descr="SSR">a.invoke(0, 0)</warning>
        }

    """.trimIndent()) }

    fun testCallAnyParameter() { doTest("'_('_*)", """
        class FirstClass {
            fun firstClassFun() { }
            
            fun firstClassFunTwo(i: Int) { <warning descr="SSR">print(i)</warning> }

            fun testFoo() {
                <warning descr="SSR">firstClassFun()</warning>
                <warning descr="SSR">firstClassFunTwo(2)</warning>
            }
        }
    """.trimIndent()) }

    fun testFunTrailingLambda() { doTest("'_:[_${MatchCallSemanticsModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})]('_+)", """
        fun a(b: () -> String) {
            b()
            <warning descr="SSR">a({"foo"})</warning>
            <warning descr="SSR">a{"foo"}</warning>
        }
    """.trimIndent()) }

    fun testFunTrailingLambdaMultiArg() { doTest("'_:[_${MatchCallSemanticsModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})]('_{2,2})", """
        fun a(i: Int, b: () -> String) {
            println(i)
            b()
            <warning descr="SSR">a(2, {"foo"})</warning>
            <warning descr="SSR">a(2) {"foo"}</warning>
        }
    """.trimIndent()) }

    fun testFqCallExpression() { doTest("A.B()", """
        class A {
            val x = B()
            val y = <warning descr="SSR">A.B()</warning>
            class B { }
        }
    """.trimIndent()) }

    fun testInfixCall() { doTest("'_ foo true", """
        infix fun Int.foo(any: Any?): Any? { return any }

        infix fun Int.bar(any: Any?): Any? { return any }

        fun main() {
            val x = 0
            <warning descr="SSR">x foo true</warning>
            x bar true
            x foo false
        }
    """.trimIndent()) }
}