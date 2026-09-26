// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.replace

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralReplaceTest

class KotlinSSRIfReplaceTest : KotlinStructuralReplaceTest() {
    fun testIfThenMultiLineFormat() {
        doTest(
            searchPattern = "if('_VAR) { '_STATEMENT }",
            replacePattern = """
                if ('_VAR) {
                    '_STATEMENT
                }
            """.trimIndent(),
            match = """
                fun main() {
                    var x = 0
                    if (x == 0) {
                        println()
                    }
                }
            """.trimIndent(),
            result = """
                fun main() {
                    var x = 0
                    if (x == 0) {
                        println()
                    }
                }
            """.trimIndent()
        )
    }

    fun testIfThenSingleLineFormat() {
        doTest(
            searchPattern = "if('_VAR) { '_STATEMENT }",
            replacePattern = """
                if ('_VAR) { '_STATEMENT }
            """.trimIndent(),
            match = """
                fun main() {
                    var x = 0
                    if (x == 0) {
                        println()
                    }
                }
            """.trimIndent(),
            result = """
                fun main() {
                    var x = 0
                    if (x == 0) { println() }
                }
            """.trimIndent()
        )
    }

    fun testIfThenNoBracketsFormat() {
        doTest(
            searchPattern = "if('_VAR) { '_STATEMENT }",
            replacePattern = """
                if ('_VAR) '_STATEMENT
            """.trimIndent(),
            match = """
                fun main() {
                    var x = 0
                    if (x == 0) {
                        println()
                    }
                }
            """.trimIndent(),
            result = """
                fun main() {
                    var x = 0
                    if (x == 0) println()
                }
            """.trimIndent()
        )
    }


    fun testIfElseNoBracketsFormat() {
        doTest(
            searchPattern = "if('_VAR) { '_STATEMENT }",
            replacePattern = """
                if ('_VAR) '_STATEMENT else '_STATEMENT
            """.trimIndent(),
            match = """
                fun main() {
                    var x = 0
                    if (x == 0) {
                        println()
                    }
                }
            """.trimIndent(),
            result = """
                fun main() {
                    var x = 0
                    if (x == 0) println() else println()
                }
            """.trimIndent()
        )
    }

    fun testIfElseSpacing() {
        doTest(
            searchPattern = "if('_VAR) { '_STATEMENTS* }",
            replacePattern = """
                if ('_VAR) {
                
                } else {
                    '_STATEMENTS
                }
            """.trimIndent(),
            match = """
                fun main() {
                    if (true) {
                      val a = 5
                      
                      val b = true
                    }
                }
            """.trimIndent(),
            result = """
                fun main() {
                    if (true) {
                
                    } else {
                        val a = 5
                
                        val b = true
                    }
                }
            """.trimIndent(),
            reformat = true
        )
    }
}