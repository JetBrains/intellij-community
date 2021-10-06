// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.replace

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSRReplaceTest

class KotlinSSRShortenFqNamesTest : KotlinSSRReplaceTest() {
    fun testPropertyTypeShortenFQReplacement() {
        doTest(
            searchPattern = "var '_ID : '_TYPE",
            replacePattern = "var '_ID : java.io.File)",
            match = "fun main() { var foo: String }",
            result = """
                import java.io.File
                
                fun main() { var foo : File }
            """.trimIndent(),
            shortenFqNames = true
        )
    }

    fun testPropertyTypeNoShortenFQReplacement() {
        doTest(
            searchPattern = "var '_ID : '_TYPE",
            replacePattern = "var '_ID : java.io.File)",
            match = "fun main() { var foo: String }",
            result = "fun main() { var foo : java.io.File }",
            shortenFqNames = false
        )
    }

    fun testExtensionFunctionReplacement() {
        myFixture.addFileToProject("Utils.kt", """
            package foo.bar
            
            fun Int.searchCall() { }
            fun Int.replaceCall() { }
        """.trimIndent())
        doTest(
            searchPattern = "'_REC.searchCall()",
            replacePattern = "'_REC.foo.bar.replaceCall()",
            match = """
                package test

                import foo.bar.searchCall

                fun main() {               
                  0.searchCall()
                }
            """.trimIndent(),
            result = """
                package test

                import foo.bar.searchCall
                import foo.bar.replaceCall

                fun main() {               
                  0.replaceCall()
                }
            """.trimIndent(),
            shortenFqNames = true
        )
    }

    // Resulting replacement is not valid Kotlin code but it makes more sense to do this replacement
    fun testExtensionFunctionNoFqReplacement() {
        myFixture.addFileToProject("Utils.kt", """
            package foo.bar
            
            fun Int.searchCall() { }
            fun Int.replaceCall() { }
        """.trimIndent())
        doTest(
            searchPattern = "'_REC.searchCall()",
            replacePattern = "'_REC.foo.bar.replaceCall()",
            match = """
                package test

                import foo.bar.searchCall

                fun main() {               
                  0.searchCall()
                }
            """.trimIndent(),
            result = """
                package test
  
                import foo.bar.searchCall

                fun main() {               
                  0.foo.bar.replaceCall()
                }
            """.trimIndent(),
            shortenFqNames = false
        )
    }
}