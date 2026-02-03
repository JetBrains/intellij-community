// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.replace

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralReplaceTest

class KotlinSSRPropertyReplaceTest : KotlinStructuralReplaceTest() {
    fun testPropertyValueReplaceExplicitType() {
        doTest(
            searchPattern = "val '_ID : String = '_INIT",
            replacePattern = "val '_ID = \"foo\"",
            match = "val foo: String = \"bar\"",
            result = "val foo: String = \"foo\""
        )
    }

    fun testPropertyValueReplaceExplicitTypeFormatCopy() {
        doTest(
            searchPattern = "val '_ID : String = '_INIT",
            replacePattern = "val '_ID = \"foo\"",
            match = "val  foo :  String  =  \"bar\"",
            result = "val foo :  String  = \"foo\""
        )
    }

    fun testPropertyNoInitializer() {
        doTest(
            searchPattern = "var '_ID : '_TYPE = '_INIT{0,1}",
            replacePattern = "var '_ID : '_TYPE = '_INIT",
            match = "var foo: String",
            result = "var foo : String"
        )
    }

    fun testPropertyInitializer() {
        doTest(
                searchPattern = "'_ID()",
                replacePattern = "'_ID()",
                match = """
                    class Foo
                    val foo = Foo()
                """.trimIndent(),
                result = """
                    class Foo
                    val foo = Foo()
                """.trimIndent()
        )
    }
}