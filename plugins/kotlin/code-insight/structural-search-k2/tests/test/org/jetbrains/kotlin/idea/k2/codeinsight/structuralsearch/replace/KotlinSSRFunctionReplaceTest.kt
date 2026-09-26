// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.replace

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralReplaceTest

class KotlinSSRFunctionReplaceTest : KotlinStructuralReplaceTest() {
    fun testVisibilityModifierCopy() {
        doTest(
            searchPattern = "fun '_ID('_PARAM*)",
            replacePattern = "fun '_ID('_PARAM)",
            match = "public fun foo() {}",
            result = "public fun foo() {}"
        )
    }

    fun testVisibilityModifierRemoval() {
        doTest(
            searchPattern = "public fun '_ID('_PARAM*)",
            replacePattern = "fun '_ID('_PARAM)",
            match = "public fun foo() {}",
            result = "fun foo() {}"
        )
    }

    fun testVisibilityModifierReplace() {
        doTest(
            searchPattern = "public fun '_ID('_PARAM*)",
            replacePattern = "private fun '_ID('_PARAM)",
            match = "public fun foo() {}",
            result = "private fun foo() {}"
        )
    }

    fun testVisibilityModifierFormatCopy() {
        doTest(
            searchPattern = "fun '_ID('_PARAM*)",
            replacePattern = "fun '_ID('_PARAM)",
            match = "public  fun foo() {}",
            result = "public  fun foo() {}"
        )
    }
    
    fun testFunctionParameterFormatCopy() {
        doTest(
            searchPattern = "fun '_ID('_PARAM)",
            replacePattern = "fun '_ID('_PARAM)",
            match = "public fun foo(bar  :  Int  =  0)  {}",
            result = "public fun foo(bar  :  Int  =  0)  {}"
        )
    }

    fun testFunctionTypedParameterFormatCopy() {
        doTest(
            searchPattern = "fun '_ID('_PARAM : '_TYPE)",
            replacePattern = "fun '_ID('_PARAM : '_TYPE)",
            match = "public fun foo(bar : Int  =  0)  {}",
            result = "public fun foo(bar : Int  =  0)  {}"
        )
    }

    fun testFunctionMultipleTypedParameterFormatCopy() {
        doTest(
            searchPattern = "fun '_ID('_PARAM* : '_TYPE)",
            replacePattern = "fun '_ID('_PARAM : '_TYPE)",
            match = "public fun foo(bar : Int  =  0, baz : Boolean = true)  {}",
            result = "public fun foo(bar : Int  =  0, baz : Boolean = true)  {}"
        )
    }

    fun testFunctionDefaultParameterFormatCopy() {
        doTest(
            searchPattern = "fun '_ID('_PARAM : '_TYPE = '_INIT)",
            replacePattern = "fun '_ID('_PARAM : '_TYPE = '_INIT)",
            match = "public fun foo(bar : Int = 0)  {}",
            result = "public fun foo(bar : Int = 0)  {}"
        )
    }

    fun testFunctionMultipleDefaultParameterFormatCopy() {
        doTest(
            searchPattern = "fun '_ID('_PARAM* : '_TYPE = '_INIT)",
            replacePattern = "fun '_ID('_PARAM : '_TYPE = '_INIT)",
            match = "public fun foo(bar : Int = 0, baz : Boolean = true)  {}",
            result = "public fun foo(bar : Int = 0, baz : Boolean = true)  {}"
        )
    }

    fun testFunctionMultiParamCountFilter() {
        doTest(
            searchPattern = "fun '_ID('_PARAM*)",
            replacePattern = "fun '_ID('_PARAM)",
            match = "fun foo(one: Int, two: Double) {}",
            result = "fun foo(one: Int, two: Double) {}"
        )
    }

    fun testFunctionInitializer() {
        doTest(
            searchPattern = "'_ID()",
            replacePattern = "'_ID()",
            match = """
                    class Foo
                    fun foo() = Foo()
                """.trimIndent(),
            result = """
                    class Foo
                    fun foo() = Foo()
                """.trimIndent()
        )
    }

    fun testExtensionSearchPattern() {
        doTest(
            searchPattern = "fun '_RECEIVER{0,1}.'_COLLECTOR()",
            replacePattern = "fun '_RECEIVER.'_COLLECTOR()",
            match = """
                    fun foo() { }
                """.trimIndent(),
            result = """
                    fun foo() { }
                """.trimIndent()
        )
    }

    fun testExtensionFunction() {
        doTest(
            searchPattern = "fun '_RECEIVER{0,1}.'_COLLECTOR(): Int",
            replacePattern = "fun '_RECEIVER.'_COLLECTOR(): Int",
            match = """
                    fun Number.foo(): Int { return 0 }
                """.trimIndent(),
            result = """
                    fun Number.foo(): Int { return 0 }
                """.trimIndent()
        )
    }

    fun testTrailingComment() {
        doTest(
            searchPattern = "fun '_ID()",
            replacePattern = "fun '_ID()",
            match = "public fun foo() = Unit // comment",
            result = "public fun foo() = Unit // comment"
        )
    }
}