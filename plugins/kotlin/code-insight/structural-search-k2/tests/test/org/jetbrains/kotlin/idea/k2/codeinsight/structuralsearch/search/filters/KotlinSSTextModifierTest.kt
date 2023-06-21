// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.search.filters

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchTest

class KotlinSSTextModifierTest : KotlinStructuralSearchTest() {
    fun testHierarchyClassName() { doTest("class '_:*[regex(Foo2)]", """
        class X {
            open class Foo
            <warning descr="SSR">open class Foo2: Foo()</warning>
            <warning descr="SSR">class Foo3 : Foo2()</warning>
        }
    """.trimIndent()) }

    fun testHierarchyClassDeclaration() { doTest("class Foo2 { val '_:*[regex(.*)] }", """
        class X {
            <warning descr="SSR">open class Foo {
                val x = 1
            }</warning>
        
            <warning descr="SSR">open class Foo2: Foo() {
                val y = 2
            }</warning>
        
            class Foo3 : Foo2() {
                val z = 3
            }
        }
        
        class Y {
            <warning descr="SSR">open class Foo<T> {
                val x = 1
            }</warning>
        
            <warning descr="SSR">open class Foo2<T> : Foo<T>() {
                val y = 2
            }</warning>
        
            class Foo3<Int> : Foo2<Int>() {
                val z = 3
            }
        }
    """.trimIndent()) }

    fun testHierarchyClassSuperType() { doTest("class '_ : '_:*[regex(Foo2)]()", """
        class X {
            open class Foo
            <warning descr="SSR">open class Foo2: Foo()</warning>
            <warning descr="SSR">class Foo3 : Foo2()</warning>
        }
    """.trimIndent()) }

    fun testFqSuperType() { doTest("class '_ : '_:[regex(test\\.Foo)]()", """
        package test
        
        open class Foo
        
        <warning descr="SSR">class Bar : Foo()</warning>
        
        class A {
            open class Foo
            class Bar2 : Foo()
        }
    """.trimIndent()) }

    fun testFqTypeAlias() { doTest("fun '_('_ : '_:[regex(test\\.OtherInt)])", """
        package test
        
        typealias OtherInt = Int
        
        <warning descr="SSR">fun foo1(x: OtherInt) { print(x) }</warning>
        
        fun bar1(x: Int) { print(x) }
        fun bar2(x: String) { print(x) }
    """.trimIndent()) }

    fun testFqClassName() { doTest("class '_:[regex(test\\.A)]", """
        package test
        
        <warning descr="SSR">class A {}</warning>
        
        class B { class A {} }
    """.trimIndent()) }
}