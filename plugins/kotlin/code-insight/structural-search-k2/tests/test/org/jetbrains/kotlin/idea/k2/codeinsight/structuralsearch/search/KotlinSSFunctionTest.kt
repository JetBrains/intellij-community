// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.search

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchTest

class KotlinSSFunctionTest : KotlinStructuralSearchTest() {
    fun testFun() { doTest(pattern = "fun a() { '_* }", highlighting = """
        <warning descr="SSR">fun a() { }</warning>
        fun b() { }
    """.trimIndent()) }

    fun testFunAny() { doTest(pattern = "fun '_( '_* )", highlighting = """
        <warning descr="SSR">fun foo1() { }</warning>
        <warning descr="SSR">fun bar(x: Int) { print(x) }</warning>
        <warning descr="SSR">fun baz(x: Int, y: Int) { print(x + y) }</warning>
    """.trimIndent()) }

    fun testFunLocal() { doTest(pattern = "fun b() { '_* }", highlighting = """
        fun a() {
            <warning descr="SSR">fun b() { }</warning>
        }
    """.trimIndent()) }

    fun testFunParam() { doTest(pattern = "fun '_(b: Int, c: String) { '_* }", highlighting = """
        <warning descr="SSR">fun a(b: Int, c: String) { }</warning>
        fun b(b: Int, c: Int) { }
        fun c(b: Int) { }
    """.trimIndent()) }

    fun testFunSameTypeParam() { doTest(pattern = "fun '_('_ : '_A, '_ : '_A) { '_* }", highlighting = """
        fun bar(a: Int, b: String) { print(a.hashCode()) ; print(b.hashCode()) }
        <warning descr="SSR">fun foo(a: Int, b: Int) { print(a.hashCode()) ; print(b.hashCode()) }</warning>
    """.trimIndent()) }

    fun testFunSingleParam() { doTest(pattern = "fun '_('_ : '_) { '_* }", highlighting = """
        <warning descr="SSR">fun foo1(p: Int) { print(p) }</warning>
        <warning descr="SSR">fun foo2(p: String) { print(p) }</warning>
        <warning descr="SSR">fun foo3(p: (Int) -> Int) { print(p(0)) }</warning>
        fun bar(p1: Int, p2: Int) { print(p1 + p2) }
    """.trimIndent()) }

    fun testFunTypeParam() { doTest(pattern = "fun<T, R> '_(a: T, b: R, c: T) { '_* }", highlighting = """
        fun foo(vararg x : Any?) = print(x.hashCode())
        <warning descr="SSR">fun<T, R> a(a: T, b: R, c: T) { foo(a, b, c) }</warning>
        fun<R, T> b(a: T, b: R, c: T) { foo(a, b, c) }
        fun<T, R> c(a: T, b: R, c: R) { foo(a, b, c) }
        fun<T> d(a: T, b: T, c: T) { foo(a, b, c) }
        fun e(a: Int, b: Int, c: Int) { foo(a, b, c) }
    """.trimIndent()) }

    fun testFunReturnType() { doTest(pattern = "fun '_(b: Int): Int { return b }", highlighting = """
        <warning descr="SSR">fun a(b: Int): Int { return b }</warning>
    """.trimIndent()) }

    fun testFunBlockBody() { doTest(pattern = """
        fun '_() {
            println()
        }
    """, highlighting = """
        <warning descr="SSR">fun a() {
            println()
        }</warning>

        fun b() {
            println()
            println()
        }
    """.trimIndent())
    }

    fun testFunPublicModifier() { doTest(pattern = "public fun '_('_*)", highlighting = """
        <warning descr="SSR">public fun foo1() { }</warning>
        val eps = 1E-10 // "good enough", could be 10^-15
        <warning descr="SSR">public tailrec fun findFixPoint(x: Double = 1.0): Double
                = if (Math.abs(x - Math.cos(x)) < eps) x else findFixPoint(Math.cos(x))</warning>
        fun bar() { }
    """.trimIndent()) }

    fun testFunInternalModifier() { doTest(pattern = "internal fun '_()", highlighting = """
        <warning descr="SSR">internal fun a() { }</warning>
        fun b() { }
    """.trimIndent()) }

    fun testFunPrivateModifier() { doTest(pattern = "private fun '_()", highlighting = """
        <warning descr="SSR">private fun a() { }</warning>
        fun b() { }
    """.trimIndent()) }

    fun testFunTypeReference() { doTest(pattern = "fun '_(): '_", highlighting = """
        fun a() = true
        <warning descr="SSR">fun b(): Boolean = true</warning>
        <warning descr="SSR">fun c(): Boolean {
            return true
        }</warning>
    """.trimIndent()) }

    fun testFunTypeReferenceCountFilter() { doTest(pattern = "fun '_(): '_{0,1}", highlighting = """
        <warning descr="SSR">fun a() = true</warning>
        <warning descr="SSR">fun b(): Boolean = true</warning>
        <warning descr="SSR">fun c(): Boolean {
            return true
        }</warning>
    """.trimIndent()) }

    fun testFunSimpleTypeReceiver() { doTest(pattern = "fun<'_type> '_('_ : '_.('_type) -> '_)", highlighting = """
        fun foo(vararg x : Any?) = print(x.hashCode())
        <warning descr="SSR">fun<T> a(a: Int.(T) -> T) { foo(a) }</warning>
        fun<R, T> b(a: T, b: R) { foo(a, b) }
        fun<T> c(a: T, b: Int) { foo(a, b) }
        fun d(a: Int.(Int) -> Int) { foo(a) }
    """.trimIndent()) }

    fun testFunReceiverType() { 
        doTest(pattern = "fun <'_T, '_E, '_R> '_name('_f : '_T.('_E) -> '_R) : ('_T, '_E) -> '_R = { '_t, '_e -> '_t.'_f('_e) }",
               highlighting = "<warning descr=\"SSR\">fun <T, E, R> foo(f: T.(E) -> R): (T, E) -> R = { t, e -> t.f(e) }</warning>"
        )
    }

    fun testFunTypeParamArgs() { doTest(pattern = "fun <'_E, '_T> '_name(p1: '_E, p2: '_T)", highlighting = """
        fun <E, T> y(p1: T, p2: T) { print(p1.hashCode()) ; print(p2.hashCode()) }
        <warning descr="SSR">fun <E, T> x(p1: E, p2: T) { print(p1.hashCode()) ; print(p2.hashCode()) }</warning>
    """.trimIndent()) }

    fun testMethod() { doTest(pattern = "fun a()", highlighting = """
        class A {
            <warning descr="SSR">fun a() { }</warning>
            fun b() { }
        }
    """.trimIndent()) }

    fun testMethodProtectedModifier() { doTest(pattern = "protected fun '_()", highlighting = """
        class A {
            <warning descr="SSR">protected fun a() { }</warning>
            fun b() { }
        }
    """.trimIndent()) }

    fun testFunExprBlock() { doTest(pattern = "fun '_(): Int = 0", highlighting = """
        <warning descr="SSR">fun a(): Int = 0</warning>
        <warning descr="SSR">fun b(): Int {
            return 0
        }</warning>
        fun c(): Int = 1
        fun d(): Int {
            return 1
        }
    """.trimIndent()) }

    fun testFunAnyExprBlock() { doTest(pattern = "fun '_() = 'EXPR", highlighting = """
        fun f(): String = <warning descr="SSR">"s"</warning>
        fun f2(): String {
            return <warning descr="SSR">""</warning>
        }
        fun f3(): String {
            println()
            return ""
        }
    """.trimIndent()) }

    fun testFunAnnotation() { doTest(pattern = "@Foo fun '_('_*)", highlighting = """
        annotation class Foo()
        annotation class Bar(val x: String)
        <warning descr="SSR">@Foo fun foo1() { }</warning>
        <warning descr="SSR">@Foo @Bar("bar") fun foo2() { }</warning>
        fun bar() { }
    """.trimIndent()) }

    fun testFunReceiverTypeReference() { doTest(pattern = "fun '_.'_()", highlighting = """
        fun myFun() {}
        <warning descr="SSR">fun Int.myFun1() {}</warning>
        <warning descr="SSR">fun kotlin.Int.myFun2() {}</warning>
    """.trimIndent()) }

    fun testFunFqReceiverTypeReference() { doTest(pattern = "fun kotlin.Int.'_()", highlighting = """
        fun myFun() {}
        fun Int.myFun1() {}
        <warning descr="SSR">fun kotlin.Int.myFun2() {}</warning>
        class A {
            class Int
            fun Int.foo() {}
        }
    """.trimIndent()) }

    fun testFunVarargParam() { doTest(pattern = "fun '_(vararg '_)", highlighting = """
        fun <T> fooTwo(par: T) { print(par) }
        <warning descr="SSR">fun <T> foo(vararg par: T) { print(par) }</warning>
    """.trimIndent()) }

    fun testFunVarargAndNormalParam() { doTest(pattern = "fun '_(vararg '_ : '_, '_ : '_)", highlighting = """
        <warning descr="SSR">fun x(vararg y: Int, z: Int) { println(y + z) }</warning>
    """.trimIndent()) }

    fun testFunVarargAndNormalReverseParam() { doTest(pattern = "fun '_('_ : '_, vararg '_ : '_)", highlighting = """
        <warning descr="SSR">fun x(z: Int, vararg y: Int) { println(y + z) }</warning>
    """.trimIndent()) }

    fun testFunVarargFullMatchParam() { doTest(pattern = "fun '_('_)", highlighting = """
        <warning descr="SSR">fun x(vararg y: Int) { println(y) }</warning>
    """.trimIndent()) }
    
    fun testFunNoinlineParam() { doTest(pattern = "fun '_(noinline '_)", highlighting = """
        inline fun <T> myInline(body: () -> T): T = body()
        <warning descr="SSR"><warning descr="[NOTHING_TO_INLINE] Expected performance impact from inlining is insignificant. Inlining works best for functions with parameters of function types.">inline</warning> fun <T> myInlineTwo(noinline body: () -> T): T = body()</warning>
        inline fun <T> myInlineThree(crossinline body: () -> T): T = body()
    """.trimIndent()) }

    fun testFunEmptyBlock() { doTest(pattern = "fun '_('_*) { '_{0,0} }", highlighting = """
        <warning descr="SSR">fun fooEmpty() {}</warning>
        fun fooSingleExpr() = 1
        fun fooOneStatementInBlock() {
            println()
        }
        fun fooTwoStatementsInBlock() {
            println()
            println()
        }
    """.trimIndent()) }

    fun testFun2ExprBlock() { doTest(pattern = "fun '_('_*) { '_{2,2} }", highlighting = """
        fun fooEmpty() {}
        fun fooSingleExpr() = 1
        fun fooOneStatementInBlock() {
            println()
        }
        <warning descr="SSR">fun fooTwoStatementsInBlock() {
            println()
            println()
        }</warning>
    """.trimIndent()) }

    fun testFunBlockBodyExprAndVariable() { doTest(pattern = """
        fun '_('_*) {
            println()
            '_{0,1}
        }
     """, highlighting = """
         <warning descr="SSR">fun a() {
             println()
         }</warning>
         <warning descr="SSR">fun b() {
             println()
             println()
         }</warning>
         fun c() {
             println()
             println()
             println()
         }
     """.trimIndent()) }

    fun testFunTypeProjection() { doTest(pattern = "fun '_('_ : A<out '_>)", highlighting = """
        interface A<T>{}
        fun fooGen(par: A<Any>) { print(par) }
        <warning descr="SSR">fun fooOut(par: A<out Any>) { print(par) }</warning>
        fun fooIn(par: A<in String>) { print(par) }
        fun fooStar(par: A<*>) { print(par) }
    """.trimIndent()) }

    fun testFunStarTypeProjection() { doTest(pattern = "fun '_('_ : A<*>)", highlighting = """
        interface A<T>{}
        fun fooGen(par: A<Any>) { print(par) }
        fun fooOut(par: A<out Any>) { print(par) }
        fun fooIn(par: A<in String>) { print(par) }
        <warning descr="SSR">fun fooStar(par: A<*>) { print(par) }</warning>
    """.trimIndent()) }
}