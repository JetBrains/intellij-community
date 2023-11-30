// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.replace

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralReplaceTest

class KotlinSSRMultiReplaceTest : KotlinStructuralReplaceTest() {
    fun testMultiReplaceRemoveFirst() {
        doTest(
            searchPattern = """
                val '_ID1 = '_VALUE1
                val '_ID2 = '_VALUE2
            """.trimIndent(),
            replacePattern = """
                val '_ID2 = '_VALUE2
            """.trimIndent(),
            match = """
                fun main() {
                    val foo = "foo"
                    val bar = "bar"
                }
            """.trimIndent(),
            result = """
                fun main() {
                    val bar = "bar"
                }
            """.trimIndent(),
        )
    }

    fun testMultiReplaceRemoveSecond() {
        doTest(
            searchPattern = """
                val '_ID1 = '_VALUE1
                val '_ID2 = '_VALUE2
            """.trimIndent(),
            replacePattern = """
                val '_ID1 = '_VALUE1
            """.trimIndent(),
            match = """
                fun main() {
                    val foo = "foo"
                    val bar = "bar"
                }
            """.trimIndent(),
            result = """
                fun main() {
                    val foo = "foo"
                }
            """.trimIndent(),
        )
    }

    fun testMultiReplaceDoubleSecond() {
        doTest(
            searchPattern = """
                val '_ID1 = '_VALUE1
                val '_ID2 = '_VALUE2
            """.trimIndent(),
            replacePattern = """
                val '_ID1 = '_VALUE1
                val '_ID2 = '_VALUE2
                val x = '_VALUE2
            """.trimIndent(),
            match = """
                fun main() {
                    val foo = "foo"
                    val bar = "bar"
                }
            """.trimIndent(),
            result = """
                fun main() {
                    val foo = "foo"
                    val bar = "bar"
                    val x = "bar"
                }
            """.trimIndent(),
        )
    }

    fun testMultiReplaceDoubleCopy() {
        doTest(
            searchPattern = """
                val '_ID1 = '_VALUE1
                val '_ID2 = '_VALUE2
            """.trimIndent(),
            replacePattern = """
                val '_ID1 = '_VALUE1
                val '_ID2 = '_VALUE2
                val x = '_VALUE1
                val y = '_VALUE2
            """.trimIndent(),
            match = """
                fun main() {
                    val foo = "foo"
                    val bar = "bar"
                }
            """.trimIndent(),
            result = """
                fun main() {
                    val foo = "foo"
                    val bar = "bar"
                    val x = "foo"
                    val y = "bar"
                }
            """.trimIndent(),
        )
    }

    fun testRemoveAll() {
        doTest(
            searchPattern = """
                val '_ID1 = '_VALUE1
            """.trimIndent(),
            replacePattern = "",
            match = """
                fun main() {
                    val foo = "foo"
                }
            """.trimIndent(),
            result = """
                fun main() {
                }
            """.trimIndent(),
        )
    }
}