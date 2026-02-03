// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSCallableReferenceTest : KotlinStructuralSearchTest() {
    fun testCallableReference() { doTest("::'_", """
        fun isOdd(x: Int) = x % 2 != 0

        fun a() {
            val numbers = listOf(1, 2, 3)
            println(numbers.filter(<warning descr="SSR">::isOdd</warning>))
        }
    """.trimIndent()) }

    fun testExtensionFun() { doTest("List<Int>::'_", """
        val isEmptyStringList: List<String>.() -> Boolean = List<String>::isEmpty

        val isEmptyIntList: List<Int>.() -> Boolean = <warning descr="SSR">List<Int>::isEmpty</warning>
    """.trimIndent()) }

    fun testPropertyReference() { doTest("::'_.name", """
        val x = 1

        fun main() {
            println(::x.get())
            println(<warning descr="SSR">::x.name</warning>)
        }
    """.trimIndent()) }
}