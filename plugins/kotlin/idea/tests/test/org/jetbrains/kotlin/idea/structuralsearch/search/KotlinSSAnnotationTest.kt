// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSAnnotationTest : KotlinStructuralSearchTest() {
    fun testAnnotation() { doTest("@Foo", """
        annotation class Foo
        annotation class Bar

        <warning descr="SSR">@Foo</warning> val foo1 = 1

        @Bar val bar1 = 1

        <warning descr="SSR">@Foo</warning> fun foo2(): Int = 1

        @Bar fun bar2(): Int = 2
    """.trimIndent()) }

    fun testAnnotationArrayParameter() { doTest("@Foo(['_*])", """
        annotation class Foo(val bar: IntArray)

        <warning descr="SSR">@Foo([1, 2, 3, 4])</warning>
        fun a() { }

        <warning descr="SSR">@Foo(intArrayOf(1, 2, 3, 4))</warning>
        fun b() { }
    """.trimIndent()) }

    fun testClassAnnotation() { doTest("@A class '_", """
        annotation class A

        annotation class B

        <warning descr="SSR">@A class C() { }</warning>

        @B class D() { }
    """.trimIndent()) }

    fun testClass2Annotations() { doTest("@'_Annotation{2,100} class '_Name", """
        annotation class FirstAnnotation
        annotation class SecondAnnotation
        annotation class ThirdAnnotation

        class ZeroClass

        @FirstAnnotation
        class FirstClass

        <warning descr="SSR">@FirstAnnotation
        @SecondAnnotation
        class SecondClass</warning>

        <warning descr="SSR">@FirstAnnotation
        @SecondAnnotation
        @ThirdAnnotation
        class ThirdClass</warning>
    """.trimIndent()) }

    fun testFunAnnotation() { doTest("@A fun '_() { println(0) }", """
        annotation class A

        annotation class B

        <warning descr="SSR">@A fun c() { println(0) }</warning>

        @B fun d() { println(0) }
    """.trimIndent()) }

    fun testClassAnnotationArgs() { doTest("@A(0) class '_()", """
        annotation class A(val x: Int)

        <warning descr="SSR">@A(0) class C() { }</warning>

        @A(1) class D() { }
    """.trimIndent()) }

    fun testUseSiteTarget() { doTest("class '_(@get:'_ val '_ : '_)", """
        @Target(AnnotationTarget.PROPERTY, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.FIELD)
        annotation class Ann

        <warning descr="SSR">class One(@get:Ann val prop: String)</warning>

        class Two(@field:Ann val prop: String)

        class Three(@Ann val prop: String)
    """.trimIndent()) }

    fun testAnnotatedExpression() { doTest("@'_{0,1} { println() }", """
        @Target(AnnotationTarget.FUNCTION)
        annotation class Ann

        fun annotatedLambda() = <warning descr="SSR">@Ann <warning descr="SSR">{ println() }</warning></warning>
        fun notAnnotatedLambda() = <warning descr="SSR">{ println() }</warning>
    """.trimIndent()) }

    fun testAnnotatedExpressionZero() { doTest("fun '_() = @'_{0,0} { println() }", """
        @Target(AnnotationTarget.FUNCTION)
        annotation class Ann

        fun annotatedLambda() = @Ann { println() }
        <warning descr="SSR">fun notAnnotatedLambda() = { println() }</warning>
    """.trimIndent()) }
}