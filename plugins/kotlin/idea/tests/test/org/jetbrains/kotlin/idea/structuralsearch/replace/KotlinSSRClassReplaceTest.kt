// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.replace

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralReplaceTest

class KotlinSSRClassReplaceTest : KotlinStructuralReplaceTest() {
    fun testClassModifierReplacement() {
        doTest(
            searchPattern = "public class '_ID('_PARAM : '_TYPE)",
            replacePattern = "internal class '_ID('_PARAM : '_TYPE)",
            match = "public class Foo(bar : Int = 0)  {}",
            result = "internal class Foo(bar : Int = 0)  {}"
        )
    }

    fun testConstructorFormatCopy() {
        doTest(
            searchPattern = "class '_ID('_PARAM : '_TYPE = '_INIT)",
            replacePattern = "class '_ID('_PARAM : '_TYPE = '_INIT)",
            match = "class Foo(bar : Int = 0)  {}",
            result = "class Foo(bar : Int = 0)  {}"
        )
    }

    fun testConstructorModifier() {
        doTest(
            searchPattern = "class '_ID('_PARAM : '_TYPE = '_INIT)",
            replacePattern = "class '_ID('_PARAM : '_TYPE = '_INIT)",
            match = "class Foo private constructor(bar : Int = 0)  {}",
            result = "class Foo private constructor(bar : Int = 0)  {}"
        )
    }

    fun testConstructorModifierReplace() {
        doTest(
            searchPattern = "class '_ID private constructor('_PARAM : '_TYPE = '_INIT)",
            replacePattern = "class '_ID public constructor('_PARAM : '_TYPE = '_INIT)",
            match = "class Foo private constructor(bar : Int = 0)  {}",
            result = "class Foo public constructor(bar : Int = 0)  {}"
        )
    }

    fun testConstructorNamedParameter() {
        doTest(
            searchPattern = "class '_ID('_PARAM : '_TYPE = '_INIT)",
            replacePattern = "class '_ID('_PARAM : '_TYPE = '_INIT)",
            match = "class Foo(bar : Int = 0)  {}",
            result = "class Foo(bar : Int = 0)  {}"
        )
    }

    fun testConstructorParameterFormatCopy() {
        doTest(
            searchPattern = "class '_ID('_PARAM : '_TYPE)",
            replacePattern = "class '_ID('_PARAM : '_TYPE)",
            match = "class Foo(bar : Int  =  0)  {}",
            result = "class Foo(bar : Int  =  0)  {}"
        )
    }

    fun testClassSuperTypesFormatCopy() {
        doTest(
            searchPattern = "class '_ID()",
            replacePattern = "class '_ID()",
            match = "class Foo()  :  Cloneable  ,  Any()  {}",
            result = "class Foo()  :  Cloneable  ,  Any()  {}"
        )
    }

    fun testClassSuperTypesFormatNoBodyCopy() {
        doTest(
            searchPattern = "class '_ID",
            replacePattern = "class '_ID",
            match = """
                interface Foo
                interface Bar

                class FooBar  :  Foo  ,  Bar
            """.trimIndent(),
            result = """
                interface Foo
                interface Bar

                class FooBar  :  Foo  ,  Bar
            """.trimIndent()
        )
    }

    fun testAnnotatedClassCountFilter() {
        doTest(
            searchPattern = "@'_ANN* class '_ID('_PARAM : '_TYPE)",
            replacePattern = "@'_ANN class '_ID('_PARAM : '_TYPE)",
            match = "class Foo(bar : Int) {}",
            result = "class Foo(bar : Int) {}"
        )
    }

    fun testClassExtensionCountFilter() {
        doTest(
            searchPattern = "class '_ID() : '_INTFACE*",
            replacePattern = "class '_ID() : '_INTFACE",
            match = "class Foo() {}",
            result = "class Foo() {}"
        )
    }

    fun testClassTypeParamCountFilter() {
        doTest(
            searchPattern = "class '_ID<'_TYPE*>()",
            replacePattern = "class '_ID<'_TYPE>()",
            match = "class Foo() {}",
            result = "class Foo() {}"
        )
    }

    fun testSimpleClassReplacement() {
        doTest(
            searchPattern = "class '_ID",
            replacePattern = "class '_ID",
            match = """
                class Foo(bar: Int) {
                    val a = "prop"
                }
            """.trimIndent(),
            result = """
                class Foo(bar: Int) {
                    val a = "prop"
                }
            """.trimIndent()
        )
    }

    fun testSimpleClassConstructorReplacement() {
        doTest(
            searchPattern = "class '_ID('_PAR : '_TYPE)",
            replacePattern = "class '_ID('_PAR : '_TYPE)",
            match = """
                class Foo(val bar : Int)
            """.trimIndent(),
            result = """
                class Foo(val bar : Int)
            """.trimIndent()
        )
    }
}