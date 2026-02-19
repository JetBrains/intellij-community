// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.search

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralSearchTest

class KotlinSSStringTemplateEntryTest : KotlinStructuralSearchTest() {
    fun testLiteral() { doTest(""" "foo" """, """
        val foo = <warning descr="SSR">"foo"</warning>
        val bar = "bar"
    """.trimIndent()) }

    fun testLiteralRegex() { doTest(""" "'_:[regex( . )] + '_:[regex( . )]" """, """
        val foo1 = <warning descr="SSR">"1 + 1"</warning>
        val foo2 = <warning descr="SSR">"2 + 8"</warning>
        val bar1 = "1 - 1"
        val bar2 = "1 + 99"
    """.trimIndent()) }

    fun testSimpleName() { doTest(""" "${"$"}foo" """, """
        val foo = 1
        val foo2 = 1
        val bar = <warning descr="SSR">"${'$'}foo"</warning>
        val bar2 = "foo"
        val bar3 = " ${'$'}foo"
    """.trimIndent()) }

    fun testLongTemplate() { doTest(""" "${"$"}{1 + 1}" """, """
        val foo = <warning descr="SSR">"${'$'}{1 + 1}"</warning>
        val bar1 = "2"
        val bar2 = "1 + 1"
        val bar3 = "${'$'}{2 + 0}"
    """.trimIndent()) }

    fun testEscape() { doTest(""" "foo\\n" """, """
        val foo = <warning descr="SSR">"foo\n"</warning>
        val bar = ""${'"'}foo\n""${'"'}
    """.trimIndent()) }

    fun testNested() { doTest(""" "${"$"}foo + 1 = ${"$"}{"${"$"}{foo + 1}"}" """, """
        val foo = 2
        val bar = <warning descr="SSR">"${'$'}foo + 1 = ${'$'}{"${'$'}{foo + 1}"}"</warning>
        val bar2 = "${'$'}foo + 1 = ${'$'}{foo + 1}"
    """.trimIndent()) }

    fun testSingleVariable() { doTest(""" "'_" """, """
        val foo = <warning descr="SSR">"foo"</warning>
        val bar = "ba${'$'}{'r'}"
        val bar2 = "${'$'}foo"
        val bar3 = "\n"
        val bar4 = "${'$'}{'.'}"
    """.trimIndent()) }

    fun testAllStrings() { doTest(""" "$$'_*" """, """
        val bar = 20
        val foo1 = <warning descr="SSR">"foo"</warning>
        val foo2 = <warning descr="SSR">"bar: bar"</warning>
        val foo3 = <warning descr="SSR">""</warning>
        val foo4 = <warning descr="SSR">"bar: ${'$'}{ bar + 1 }"</warning>
        val foo5 = <warning descr="SSR">"bar: ${'$'}{ <warning descr="SSR">"${'$'}bar"</warning> }"</warning>
        val foo6 = <warning descr="SSR">"bar: ${'$'}bar, bar - 1: ${'$'}{ bar - 1 }\n"</warning>
        val foo7 = <warning descr="SSR">(<warning descr="SSR">"foo"</warning>)</warning>
        val foo8 = <warning descr="SSR">(<warning descr="SSR">(<warning descr="SSR">"foo"</warning>)</warning>)</warning>
    """.trimIndent()) }

    fun testStringsContainingLongTemplate() { doTest(""" "$$'_*${'$'}{ '_ }$$'_*" """, """
        val bar = 20
        val foo1 = "foo"
        val foo2 = "bar: bar"
        val foo3 = ""
        val foo4 = <warning descr="SSR">"bar: ${'$'}{ bar + 1 }"</warning>
        val foo5 = <warning descr="SSR">"bar: ${'$'}{ <warning descr="SSR">"${'$'}{ bar }"</warning> }"</warning>
        val foo6 = <warning descr="SSR">"bar: ${'$'}bar, bar - 1: ${'$'}{ bar - 1 }"</warning>
    """.trimIndent()) }

    fun testStringWithBinaryExpression() { doTest(""" "${"$"}{3 * 2 + 1}" """, """
        val a = <warning descr="SSR">"${'$'}{3 * 2 + 1}"</warning>

        val b = "${'$'}{3 * (2 + 1)}"
    """.trimIndent()) }

    fun testStringBracesTemplate() { doTest(""" "Hello world! ${"$"}a" """, """
        val a = 0

        val b = <warning descr="SSR">"Hello world! ${'$'}a"</warning>

        val c = <warning descr="SSR">"Hello world! ${'$'}{a}"</warning>
    """.trimIndent()) }

    fun testStringBracesTemplateQuery() { doTest(""" "Hello world! ${"$"}{a}" """, """
        val a = 0

        val b = "Hello world! ${'$'}a"

        val c = <warning descr="SSR">"Hello world! ${'$'}{a}"</warning>
    """.trimIndent()) }
}