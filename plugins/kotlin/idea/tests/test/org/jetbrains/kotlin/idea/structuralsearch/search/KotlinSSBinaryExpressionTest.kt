// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSBinaryExpressionTest : KotlinStructuralSearchTest() {
    override fun getBasePath(): String = "binaryExpression"

    fun testNestedBinaryExpression() { doTest("1 + 2 - 3", """
        val a = <warning descr="SSR">1 + 2 - 3</warning>
    """.trimIndent()) }

    fun testBinaryParExpression() { doTest("3 * (2 - 3)", """
        val a = <warning descr="SSR">3 * (2 - 3)</warning>
        val b = 3 * 2 - 3
    """.trimIndent()) }

    fun testTwoBinaryExpressions() { doTest("""
        a = 1 
        b = 2
    """.trimIndent(), """
        fun a() {
            var a = 0
            var b = 0
            print(a + b)
            <warning descr="SSR">a = 1</warning>
            b = 2
            print(a + b)
            a = 1
            print(a + b)
        }
    """.trimIndent()) }

    fun testBinarySameVariable() { doTest("'_x + '_x", """
        fun main() {
            val foo = 1
            val bar = 1
            print(foo + 1)
            print(<warning descr="SSR">foo + foo</warning>)
            print(foo + bar)
        }
    """.trimIndent()) }

    fun testBinaryPlus() { doTest("1 + 2", """
        val a = <warning descr="SSR">1 + 2</warning>
        val b = 1.plus(2)
        val c = 1 + 3
        val d = 1.plus(3)
        val e = 1.minus(2)
    """.trimIndent()) }

    fun testBinaryMinus() { doTest("1 - 2", """
        val a = <warning descr="SSR">1 - 2</warning>
        val b = 1.minus(2)
        val c = 1 - 3
        val d = 1.minus(3)
    """.trimIndent()) }

    fun testBinaryTimes() { doTest("1 * 2", """
        val a = <warning descr="SSR">1 * 2</warning>
        val b = 1.times(2)
        val c = 1 * 3
        val d = 1.times(3)
    """.trimIndent()) }

    fun testBinaryDiv() { doTest("1 / 2", """
        val a = <warning descr="SSR">1 / 2</warning>
        val b = 1.div(2)
        val c = 1 / 3
        val d = 1.div(3)
    """.trimIndent()) }

    fun testBinaryRem() { doTest("1 % 2", """
        val a = <warning descr="SSR">1 % 2</warning>
        val b = 1.rem(2)
        val c = 1 % 3
        val d = 1.rem(3)
    """.trimIndent()) }

    fun testBinaryRangeTo() { doTest("1..2", """
        val a = <warning descr="SSR">1..2</warning>
        val b = 1.rangeTo(2)
        val c = 1..3
        val d = 1.rangeTo(3)
    """.trimIndent()) }

    fun testBinaryIn() { doTest("1 in 0..2", """
        val a = <warning descr="SSR">1 in 0..2</warning>
        val b = (0..2).contains(1)
        val c = (0..1).contains(1)
        val d = (0..2).contains(2)
        val e = 2 in 0..2
        val f = 1 in 1..2
        val g = 1 !in 0..2
        val h = !(0..2).contains(1)
    """.trimIndent()) }

    fun testBinaryNotIn() { doTest("1 !in 0..2", """
        val a = <warning descr="SSR">1 !in 0..2</warning>
        val b = !(0..2).contains(1)
        val c = !(0..1).contains(1)
        val d = !(0..2).contains(2)
        val e = 2 !in 0..2
        val f = 1 !in 1..2
        val g = 1 in 0..2
        val h = (0..2).contains(1)
        val i = !((0..2).contains(1))
    """.trimIndent()) }

    fun testBinaryBigThan() { doTest("1 > 2", """
        val a = <warning descr="SSR">1 > 2</warning>
        val b = 1.compareTo(2) > 0
        val c = 1 > 3
        val d = 1.compareTo(3) > 0
        val e = 1.compareTo(2) < 0
        val f = 2.compareTo(2) > 0
        val g = 1.compareTo(2) > 1
    """.trimIndent()) }

    fun testBinaryLessThan() { doTest("1 < 2", """
        val a = <warning descr="SSR">1 < 2</warning>
        val b = 1.compareTo(2) < 0
        val c = 1 < 3
        val d = 1.compareTo(3) < 0
        val e = 1.compareTo(2) > 0
        val f = 2.compareTo(2) < 0
        val g = 1.compareTo(2) < 1
    """.trimIndent()) }

    fun testBinaryBigEqThan() { doTest("1 >= 2", """
        val a = <warning descr="SSR">1 >= 2</warning>
        val b = 1.compareTo(2) >= 0
        val c = 1 >= 3
        val d = 1.compareTo(3) >= 0
        val e = 1.compareTo(2) <= 0
        val f = 2.compareTo(2) >= 0
        val g = 1.compareTo(2) >= 1
    """.trimIndent()) }

    fun testBinaryLessEqThan() { doTest("1 <= 2", """
        val a = <warning descr="SSR">1 <= 2</warning>
        val b = 1.compareTo(2) <= 0
        val c = 1 <= 3
        val d = 1.compareTo(3) >= 0
        val e = 1.compareTo(2) >= 0
        val f = 2.compareTo(2) <= 0
        val g = 1.compareTo(2) <= 1
    """.trimIndent()) }

    fun testBinaryEquality() { doTest("a == b", """
        var a: String? = "Hello world"
        var b: String? = "Hello world"
        var c: String? = "Hello world"
        val d = <warning descr="SSR">a == b</warning>
        val e = a?.equals(b) ?: (b === null)
        val f = a == c
        val g = c == b
        val h = a?.equals(c) ?: (b === null)
        val i = c?.equals(b) ?: (b === null)
        val j = a?.equals(b) ?: (c === null)
        val k = a?.equals(b)
        val l = a === b
        val m = a != b
    """.trimIndent()) }

    fun testBinaryInEquality() { doTest("a != b", """
        var a: String? = "Hello world"
        var b: String? = "Hello world"
        var c: String? = "Hello world"
        val d = <warning descr="SSR">a != b</warning>
        val e = !(a?.equals(b) ?: (b === null))
        val f = a != c
        val g = c != b
        val h = !(a?.equals(c) ?: (b === null))
        val i = !(c?.equals(b) ?: (b === null))
        val j = !(a?.equals(b) ?: (c === null))
        val k = !(a?.equals(b)!!)
        val l = a !== b
        val m = a == b
    """.trimIndent()) }

    fun testElvis() { doTest("'_ ?: '_", """
        fun main() {
            var a: Int? = 1
            val b = 2
            print(<warning descr="SSR">(<warning descr="SSR">a ?: 0</warning>)</warning> + b)
        }
    """.trimIndent()) }

    fun testBinaryPlusAssign() { doTest("'_ += '_", """
        class Foo {
            operator fun plusAssign(other: Foo) { print(other) }
        }

        fun main() {
            var z = 1
            <warning descr="SSR">z += 2</warning>
            z = z + 2
            print(z)
            var x = Foo()
            val y = Foo()
            x.plusAssign(y)
        }
    """.trimIndent()) }

    fun testBinaryAssignPlus() { doTest("'_ = '_ + '_", """
        class Foo {
            operator fun plusAssign(other: Foo) { print(other) }
        }

        fun main() {
            var z = 1
            z += 2
            <warning descr="SSR">z = z + 2</warning>
            print(z)
            var x = Foo()
            val y = Foo()
            x.plusAssign(y)
        }        
    """.trimIndent()) }

    fun testBinaryMinusAssign() { doTest("'_ -= '_", """
        class Foo {
            operator fun minusAssign(other: Foo) { print(other) }
        }

        fun foo() {
            var z = 1
            <warning descr="SSR">z -= 2</warning>
            z = z - 2
            print(z)
            var x = Foo()
            val y = Foo()
            x.minusAssign(y)
        }
    """.trimIndent()) }

    fun testBinaryAssignMinus() { doTest("'_ = '_ - '_", """
        class Foo {
            operator fun minusAssign(other: Foo) { print(other) }
        }

        fun foo() {
            var z = 1
            z -= 2
            <warning descr="SSR">z = z - 2</warning>
            print(z)
            var x = Foo()
            val y = Foo()
            x.minusAssign(y)
        }
    """.trimIndent()) }

    fun testBinaryTimesAssign() { doTest("'_ *= '_", """
        class Foo {
            operator fun timesAssign(other: Foo) { print(other) }
        }

        fun foo() {
            var z = 1
            <warning descr="SSR">z *= 2</warning>
            z = z * 2
            print(z)
            var x = Foo()
            val y = Foo()
            x.timesAssign(y)
        }
    """.trimIndent()) }

    fun testBinaryAssignTimes() { doTest("'_ = '_ * '_", """
        class Foo {
            operator fun timesAssign(other: Foo) { print(other) }
        }

        fun foo() {
            var z = 1
            z *= 2
            <warning descr="SSR">z = z * 2</warning>
            print(z)
            var x = Foo()
            val y = Foo()
            x.timesAssign(y)
        }
    """.trimIndent()) }

    fun testBinaryDivAssign() { doTest("'_ /= '_", """
        class Foo {
            operator fun divAssign(other: Foo) { print(other) }
        }

        fun foo() {
            var z = 1
            <warning descr="SSR">z /= 2</warning>
            z = z / 2
            print(z)
            var x = Foo()
            val y = Foo()
            x.divAssign(y)
        }
    """.trimIndent()) }

    fun testBinaryAssignDiv() { doTest("'_ = '_ / '_", """
        class Foo {
            operator fun divAssign(other: Foo) { print(other) }
        }

        fun foo() {
            var z = 1
            z /= 2
            <warning descr="SSR">z = z / 2</warning>
            print(z)
            var x = Foo()
            val y = Foo()
            x.divAssign(y)
        }
    """.trimIndent()) }

    fun testBinaryRemAssign() { doTest("'_ %= '_", """
        class Foo {
            operator fun remAssign(other: Foo) { print(other) }
        }

        fun foo() {
            var z = 1
            <warning descr="SSR">z %= 2</warning>
            z = z % 2
            print(z)
            var x = Foo()
            val y = Foo()
            x.remAssign(y)
        }
    """.trimIndent()) }

    fun testBinaryAssignRem() { doTest("'_ = '_ % '_", """
        class Foo {
            operator fun remAssign(other: Foo) { print(other) }
        }

        fun foo() {
            var z = 1
            z %= 2
            <warning descr="SSR">z = z % 2</warning>
            print(z)
            var x = Foo()
            val y = Foo()
            x.remAssign(y)
        }
    """.trimIndent()) }

    fun testBinarySet() { doTest("a[0] = 1 + 2", """
        val a = intArrayOf(0, 1)

        fun b() {
            <warning descr="SSR">a[0] = 1 + 2</warning>
            a.set(0, 1 + 2)
            a.set(0, 1)
            a.set(1, 1 + 2)
            val c = intArrayOf(1, 1)
            c.set(0, 1 + 2)
            a[0] = 1
            a[1] = 1 + 2
        }
    """.trimIndent()) }
}