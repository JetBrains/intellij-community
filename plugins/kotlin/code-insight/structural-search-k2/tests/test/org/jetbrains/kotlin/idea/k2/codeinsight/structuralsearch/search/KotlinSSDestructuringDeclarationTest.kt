// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.search

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchTest

class KotlinSSDestructuringDeclarationTest : KotlinStructuralSearchTest() {
    fun testDataClass() { doTest("val ('_, '_, '_) = '_", """
        data class Foo(val foo1: Int, val foo2: Int)
        data class Bar(val bar1: String, val bar2: String, val bar3: String)

        fun foo() = Foo(1, 2)
        fun bar() = Bar("a", "b", "c")

        fun main() {
            val (f1, f2) = foo()
            <warning descr="SSR">val (b1, b2, b3) = bar()</warning>
            print(f1 + f2)
            print(b1 + b2 + b3)

            val l = listOf(1, 3, 5)
            for ((l1, l2) in l.withIndex()) { print(l1 + l2) }
        }
    """.trimIndent()) }

    fun testLoop() { doTest("for (('_, '_) in '_) { '_* }", """
        data class Foo(val foo1: Int, val foo2: Int)
        data class Bar(val bar1: String, val bar2: String, val bar3: String)

        fun foo() = Foo(1, 2)
        fun bar() = Bar("a", "b", "c")

        fun main() {
            val (f1, f2) = foo()
            val (b1, b2, b3) = bar()
            print(f1 + f2)
            print(b1 + b2 + b3)

            val l = listOf(1, 3, 5)
            <warning descr="SSR">for ((l1, l2) in l.withIndex()) { print(l1 + l2) }</warning>
        }
    """.trimIndent()) }

    fun testVariable() { doTest("{ '_ -> '_* }", """
        fun eat(vararg elements: Any) { elements.hashCode() }

        data class Person(val name: String, val age: Int)

        fun checkLambda(block: (Person) -> Unit) { block(Person("a", 1)) }

        fun main() {
            val person = Person("Bob", 2)
            val (name, age) = person
            eat(name, age)
            checkLambda <warning descr="SSR">{ (n, a) -> eat(n, a) }</warning>
            val personList = listOf(person)
            for ((n, a) in personList) { eat(n, a) }
        }
    """.trimIndent()) }

    fun testVariableFor() { doTest("for ('_ in '_) { '_* }", """
        fun eat(vararg elements: Any) { elements.hashCode() }

        data class Person(val name: String, val age: Int)

        fun checkLambda(block: (Person) -> Unit) { block(Person("a", 1)) }

        fun main() {
            val person = Person("Bob", 2)
            val (name, age) = person
            eat(name, age)
            checkLambda { (n, a) -> eat(n, a) }
            val personList = Array(1, { person })
            <warning descr="SSR">for ((n, a) in personList) { eat(n, a) }</warning>
        }
    """.trimIndent()) }
}