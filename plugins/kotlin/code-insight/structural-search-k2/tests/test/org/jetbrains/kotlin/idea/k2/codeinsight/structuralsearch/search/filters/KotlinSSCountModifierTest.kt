// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.search.filters

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchTest

class KotlinSSCountModifierTest : KotlinStructuralSearchTest() {
    fun testMinProperty() { doTest("var '_ = '_{0,0}", """
        class A {
            <warning descr="SSR">lateinit var x: String</warning>
            var y = 1
            fun init() { x = "a" }
        }
    """.trimIndent()) }

    fun testMinFunctionTypeReference() { doTest("fun '_{0,0}.'_()", """
        class One

        fun One.myFun() {}
        <warning descr="SSR">fun myFun() {}</warning>
    """.trimIndent()) }

    fun testMinCallableReferenceExpression() { doTest("'_{0,0}::'_", """
        fun Int.isOddExtension() = this % 2 != 0

        class MyClazz {
            fun isOddMember(x: Int) = x % 2 != 0
            fun constructorReference(init: () -> MyClazz) { print(init) }

            fun foo() {
                val functionTwo: (Int) -> Boolean = Int::isOddExtension
                val functionOne: (Int) -> Boolean = <warning descr="SSR">::isOddMember</warning>
                constructorReference(<warning descr="SSR">::MyClazz</warning>)
                print(functionOne(1) == functionTwo(2))
            }
        }
    """.trimIndent()) }

    fun testMinWhenExpression() { doTest("when ('_{0,0}) {}", """
        fun foo() {
            print(<warning descr="SSR">when (1) {
                in 0..3 -> 2
                else -> 3
            }</warning>)
            print(<warning descr="SSR">when { else -> true }</warning>)
        }
    """.trimIndent()) }

    fun testMinConstructorCallee() { doTest("class '_ : '_{0,0}('_*)", """
        <warning descr="SSR">open class Foo</warning>
        class Bar: Foo()
    """.trimIndent()) }

    fun testMinSuperType() { doTest("class '_ : '_{0,0}()", """
        <warning descr="SSR">open class A</warning>
        class B : A()
    """.trimIndent()) }

    // isApplicableMaxCount

    fun testMaxDestructuringDeclarationEntry() { doTest("for (('_{3,3}) in '_) { '_* }", """
        data class Foo(val foo1: Int, val foo2: Int)
        data class Bar(val bar1: String, val bar2: String, val bar3: String)

        fun foo() = Foo(1, 2)
        fun bar() = Bar("a", "b", "c")

        fun main() {
            val (f1, f2) = foo()
            val (b1, b2, b3) = bar()
            print(f1 + f2)
            print(b1 + b2 + b3)
            val l1 = listOf(Foo(1, 1))
            for ((x1, x2) in l1) { print(x1 + x2) }
            val l2 = listOf(Bar("a", "a", "a"))
            <warning descr="SSR">for ((x1, x2, x3) in l2) { print(x1 + x2 + x3) }</warning>
            for (i in 1..2) { print(i) }
        }
    """.trimIndent()) }

    fun testMaxWhenConditionWithExpression() { doTest("when ('_?) { '_{2,2} -> '_ }", """
        fun foo(): Int {
            fun f() {}
            <warning descr="SSR">when (1) {
                 in 1..10 -> f()
                 in 11..20 -> f()
            }</warning>
            val x1 = when { else -> 1 }
            val x2 = <warning descr="SSR">when {
                1 < 2 -> 3
                else -> 1
            }</warning>
            val x3 = when {
                1 < 3 -> 1
                2 > 1 -> 4
                else -> 1
            }
            return x1 + x2 + x3
        }
    """.trimIndent()) }

    fun testMmClassBodyElement() {
        doTest("""
            class '_Class {  
                var '_Field{0,2} = '_Init?
            }
        """.trimIndent(), """
            <warning descr="SSR">class A</warning>

            <warning descr="SSR">class B {
                var x = 1
            }</warning>

            <warning descr="SSR">class C {
                var x = 1
                var y = "1"
            }</warning>

            class D {
                var x = 1
                var y = "1"
                var z = false
            }
        """.trimIndent()
        )
    }

    fun testMmParameter() { doTest("fun '_('_{0,2})", """
        <warning descr="SSR">fun foo1() {}</warning>
        <warning descr="SSR">fun foo2(p1: Int) { print(p1) }</warning>
        <warning descr="SSR">fun foo3(p1: Int, p2: Int) { print(p1 + p2) }</warning>
        fun bar(p1: Int, p2: Int, p3: Int) { print(p1 + p2 + p3) }
    """.trimIndent()) }

    fun testMmTypeParameter() { doTest("fun <'_{0,2}> '_('_*)", """
        <warning descr="SSR">fun foo1() {}</warning>
        <warning descr="SSR">fun <A> foo2(x: A) { x.hashCode() }</warning>
        <warning descr="SSR">fun <A, B> foo3(x: A, y: B) { x.hashCode() + y.hashCode() }</warning>
        fun <A, B, C> bar(x: A, y: B, z: C) { x.hashCode() + y.hashCode() + z.hashCode() }
    """.trimIndent()) }

    fun testMmTypeParameterFunctionType() { doTest("fun '_('_ : ('_{0,2}) -> '_)", """
        <warning descr="SSR">fun foo1(x : () -> Unit) { print(x) }</warning>
        <warning descr="SSR">fun foo2(x : (Int) -> Unit) { print(x) }</warning>
        <warning descr="SSR">fun foo3(x : (Int, String) -> Unit) { print(x) }</warning>
        fun bar(x : (Int, String, Boolean) -> Unit) { print(x) }
    """.trimIndent()) }

    fun testMmTypeReference() { doTest("val '_ : ('_{0,2}) -> '_", """
        <warning descr="SSR">val foo1 : () -> String = { "" }</warning>
        <warning descr="SSR">val foo2 : (Int) -> String = { x -> "${"$"}x" }</warning>
        <warning descr="SSR">val foo3 : (Int, String) -> String = { x, y -> "${"$"}x${"$"}y" }</warning>
        val bar : (Int, String, Boolean) -> String = { x, y, z -> "${"$"}x${"$"}y${"$"}z" }
    """.trimIndent()) }

    fun testMmSuperTypeEntry() { doTest("class '_ : '_{0,2}", """
        interface IOne
        interface ITwo
        interface IThree
        <warning descr="SSR">open class A</warning>
        <warning descr="SSR">class B : IOne</warning>
        <warning descr="SSR">class B2 : IOne, A()</warning>
        <warning descr="SSR">class C : IOne, ITwo</warning>
        <warning descr="SSR">class C2 : IOne, ITwo, A()</warning>
        class D : IOne, ITwo, IThree
        class D2 : IOne, ITwo, IThree, A()

    """.trimIndent()) }

    fun testMmValueArgument() { doTest("listOf('_{0,2})", """
        val foo1: List<Int> = <warning descr="SSR">listOf()</warning>
        val foo2 = <warning descr="SSR">listOf(1)</warning>
        val foo3 = <warning descr="SSR">listOf(1, 2)</warning>
        val bar = listOf(1, 2, 3)
    """.trimIndent()) }

    fun testMmStatementInDoWhile() { doTest("do { '_{0,2} } while ('_)", """
        fun foo() {
            var x = 0
            <warning descr="SSR">do { } while (false)</warning>
            <warning descr="SSR">do {
                x += 1
            } while (false)</warning>
            <warning descr="SSR">do {
                x += 1
                x *= 2
            } while (false)</warning>
            do {
                x += 1
                x *= 2
                x *= x
            } while (false)
            print(x)
        }

    """.trimIndent()) }

    fun testMmStatementInBlock() { doTest("fun '_('_*) { '_{0,2} }", """
        <warning descr="SSR">fun foo1() {}</warning>
        <warning descr="SSR">fun foo2() {
            print(1)
        }</warning>
        <warning descr="SSR">fun foo3() {
            print(1)
            print(2)
        }</warning>
        fun bar() {
            print(1)
            print(2)
            print(3)
        }
    """.trimIndent()) }

    fun testMmAnnotation() { doTest("@'_{0,2} class '_", """
        <warning descr="SSR">annotation class FirstAnnotation</warning>
        <warning descr="SSR">annotation class SecondAnnotation</warning>
        <warning descr="SSR">annotation class ThirdAnnotation</warning>
        <warning descr="SSR">class ZeroClass</warning>
        <warning descr="SSR">@FirstAnnotation class FirstClass</warning>
        <warning descr="SSR">@FirstAnnotation @SecondAnnotation class SecondClass</warning>
        @FirstAnnotation @SecondAnnotation @ThirdAnnotation class ThirdClass
    """.trimIndent()) }

    fun testMmSimpleNameStringTemplateEntry() { doTest(""" "$$'_{0,2}" """, """
        val foo1 = <warning descr="SSR">""</warning>
        val foo2 = <warning descr="SSR">"foo"</warning>
        val foo3 = <warning descr="SSR">"foo${"$"}foo1"</warning>
        val bar = "foo${"$"}foo1${"$"}foo2"
    """.trimIndent()) }
    
    fun testMmTypeProjection() { doTest("fun '_('_ : '_<'_{0,2}>)", """
        class X<A> {}
        class Y<A, B> {}
        class Z<A, B, C> {}
        <warning descr="SSR">fun foo1(par: Int) { print(par) }</warning>
        <warning descr="SSR">fun foo2(par: X<String>) { print(par) }</warning>
        <warning descr="SSR">fun foo3(par: Y<String, Int>) { print(par) }</warning>
        fun bar(par: Z<String, Int, Boolean>) { print(par) }
    """.trimIndent()) }

    fun testMmKDocTag() { doTest("""
        /**
         * @'_{0,2}
         */
    """.trimIndent(), """
        <warning descr="SSR">/**
         * lorem
         */</warning>

        <warning descr="SSR">/**
         * ipsum
         * @a
         */</warning>

        <warning descr="SSR">/**
         * dolor
         * @a
         * @b
         */</warning>

        /**
         * sit
         * @a
         * @b
         * @c
         */
    """.trimIndent()) }

    // Misc

    fun testZeroLambdaParameter() { doTest("{ '_{0,0} -> '_ }", """
        val p0: () -> Int = <warning descr="SSR">{ 31 }</warning>
        val p1: (Int) -> Int = { x -> x }
        val p1b: (Int) -> Int = <warning descr="SSR">{ it }</warning>
        val p2: (Int, Int) -> Int = { x, y -> x + y }
        val p3: (Int, Int, Int) -> Int = { x, y, z -> x + y + z }
    """.trimIndent()) }

    fun testOneLambdaParameter() { doTest("{ '_{1,1} -> '_ }", """
        val p0: () -> Int = { 31 }
        val p1: (Int) -> Int = <warning descr="SSR">{ x -> x }</warning>
        val p1b: (Int) -> Int = { it }
        val p2: (Int, Int) -> Int = { x, y -> x + y }
        val p3: (Int, Int, Int) -> Int = { x, y, z -> x + y + z }
    """.trimIndent()) }

    fun testMmLambdaParameter() { doTest("{ '_{0,2} -> '_ }", """
        val p0: () -> Int = <warning descr="SSR">{ 31 }</warning>
        val p1: (Int) -> Int = <warning descr="SSR">{ x -> x }</warning>
        val p1b: (Int) -> Int = <warning descr="SSR">{ it }</warning>
        val p2: (Int, Int) -> Int = <warning descr="SSR">{ x, y -> x + y }</warning>
        val p3: (Int, Int, Int) -> Int = { x, y, z -> x + y + z }
    """.trimIndent()) }

    fun testQualifiedExpressionNoReceiver() { doTest("'_{0,0}.'_", """
        class A {
            companion object {
                const val FOO = 3.14
            }
        }

        fun main() {
            val a = A.FOO
            <warning descr="SSR">print(Int.hashCode())</warning>
            <warning descr="SSR">print(<warning descr="SSR">a</warning>)</warning>
        }
    """.trimIndent()) }

    fun testCallExpressionOptionalReceiver() {
        myFixture.addFileToProject("pkg/A.kt", """
            package pkg;
            
            class A {
                companion object {
                    fun foo() { }
                }
            }
        """.trimIndent())
        doTest("'_{0,1}.'_()", """
            import pkg.A
            import pkg.A.Companion.foo
            
            fun main() {
                <warning descr="SSR">foo()</warning>
                <warning descr="SSR">A.foo()</warning>
            }
        """.trimIndent())
    }

    fun testCallExpressionWithReceiver() {
        myFixture.addFileToProject("pkg/A.kt", """
            package pkg;
            
            class A {
                companion object {
                    fun foo() { }
                }
            }
        """.trimIndent())
        doTest("'_{1,1}.'_()", """
            import pkg.A
            import pkg.A.Companion.foo
            
            fun main() {
                foo()
                <warning descr="SSR">A.foo()</warning>
            }
        """.trimIndent())
    }
}