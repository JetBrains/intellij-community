// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.search

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchTest

class KotlinSSTypeReferenceTest : KotlinStructuralSearchTest() {
    fun testAny() { doTest(pattern = "fun '_('_ : '_) { '_* }", highlighting = """
        class Foo { class Int }
        <warning descr="SSR">fun foo1 (bar: Int = 1) { print(bar.hashCode()) }</warning>
        <warning descr="SSR">fun foo2 (bar: kotlin.Int) { print(bar.hashCode()) }</warning>
        <warning descr="SSR">fun foo3 (bar: Int?) { print(bar.hashCode()) }</warning>
        <warning descr="SSR">fun foo4 (bar: (Int) -> Int) { print(bar.hashCode()) }</warning>
        <warning descr="SSR">fun foo5 (bar: Foo.Int) { print(bar.hashCode()) }</warning>
    """.trimIndent()) }

    fun testFqType() { doTest(pattern = "fun '_('_ : kotlin.Int) { '_* }", highlighting = """
        class Foo { class Int }
        fun foo1 (bar: Int = 1) { print(bar.hashCode()) }
        <warning descr="SSR">fun foo2 (bar: kotlin.Int) { print(bar.hashCode()) }</warning>
        fun foo3 (bar: Int?) { print(bar.hashCode()) }
        fun foo4 (bar: (Int) -> Int) { print(bar.hashCode()) }
        fun foo5 (bar: Foo.Int) { print(bar.hashCode()) }
    """.trimIndent()) }

    fun testFunctionType() { doTest(pattern = "fun '_('_ : ('_) -> '_) { '_* }", highlighting = """
        fun foo1 (bar: Int) { print(bar.hashCode()) }
        fun foo2 (bar: kotlin.Int) { print(bar.hashCode()) }
        fun foo3 (bar: Int?) { print(bar.hashCode()) }
        <warning descr="SSR">fun foo4 (bar: (Int) -> Int) { print(bar.hashCode()) }</warning>
    """.trimIndent()) }

    fun testNullableType() { doTest(pattern = "fun '_('_ : '_ ?) { '_* }", highlighting = """
        fun foo1 (bar: Int) { print(bar.hashCode()) }
        fun foo2 (bar: kotlin.Int) { print(bar.hashCode()) }
        <warning descr="SSR">fun foo3 (bar: Int?) { print(bar.hashCode()) }</warning>
        fun foo4 (bar: (Int) -> Int) { print(bar.hashCode()) }
    """.trimIndent()) }
    
    fun testFqTextFilter() { doTest(pattern = "fun '_('_ : '_:[regex( kotlin\\.Int )])", highlighting = """
        <warning descr="SSR">fun foo1(x : Int) { print(x) }</warning>
        <warning descr="SSR">fun foo2(x : kotlin.Int) { print(x) }</warning>
        class X {
            class Int
            fun bar(x : Int) { print(x) }
        }
    """.trimIndent()) }

    fun testStandaloneNullable() { doTest(pattern = "Int?", highlighting = """
        val foo: <warning descr="SSR">Int?</warning> = 1
        var bar: Int = 1
    """.trimIndent()) }

    fun testStandaloneParameter() { doTest(pattern = "Array<Int>", highlighting = """
        val foo: <warning descr="SSR">Array<Int></warning> = arrayOf()
        var bar: Array<String> = arrayOf()
    """.trimIndent()) }
}