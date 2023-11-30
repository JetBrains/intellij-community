// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.search

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchTest

class KotlinSSQualifiedExpressionTest : KotlinStructuralSearchTest() {
    fun testDotRegular() { doTest("'_.'_", """
        class A {
            companion object {
                const val FOO = 3.14
            }
        }

        fun main() {
            val a = <warning descr="SSR">A.FOO</warning>
            print(<warning descr="SSR">Int.hashCode()</warning>)
            print(a)
        }
    """.trimIndent()) }

    fun testSafeAccess() { doTest("\$e1\$?.'_", """
        fun main() {
            val a: List<Int>? = null
            print(<warning descr="SSR">a?.size</warning>)
            val b = listOf(1, 2, 3)
            print(b.size)
        }
    """.trimIndent()) }

    fun testDotNoReceiver() { doTest("'_{0,0}.'_()", """
        class MyClass {
            fun foo() {}

            fun secondClassTestFun() {
                <warning descr="SSR">foo()</warning>
                this.foo()
                MyClass().foo()
                val myClass = <warning descr="SSR">MyClass()</warning>
                myClass.foo()
            }
        }
    """.trimIndent()) }
}