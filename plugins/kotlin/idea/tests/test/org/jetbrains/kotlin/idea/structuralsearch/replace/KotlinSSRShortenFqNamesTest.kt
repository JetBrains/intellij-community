// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structuralsearch.replace

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralReplaceTest

class KotlinSSRShortenFqNamesTest : KotlinStructuralReplaceTest() {
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

    fun testExtensionFunctionReplacementImportIsBefore() {
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
                
                import foo.bar.replaceCall
                import foo.bar.searchCall
                
                fun main() {               
                  0.replaceCall()
                }
            """.trimIndent(),
            shortenFqNames = true
        )
    }

    fun testExtensionFunctionReplacementImportIsAfter() {
        myFixture.addFileToProject("Utils.kt", """
            package foo.bar
            
            fun Int.aSearchCall() { }
            fun Int.replaceCall() { }
        """.trimIndent())
        doTest(
            searchPattern = "'_REC.searchCall()",
            replacePattern = "'_REC.foo.bar.replaceCall()",
            match = """
                package test
                
                import foo.bar.aSearchCall
                
                fun main() {               
                  0.searchCall()
                }
            """.trimIndent(),
            result = """
                package test
                
                import foo.bar.aSearchCall
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