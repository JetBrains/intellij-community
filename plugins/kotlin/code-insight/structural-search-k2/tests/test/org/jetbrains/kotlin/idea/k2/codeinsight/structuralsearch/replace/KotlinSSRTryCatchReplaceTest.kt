// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.replace

import org.jetbrains.kotlin.idea.k2.codeinsight.structuralsearch.KotlinStructuralReplaceTest

class KotlinSSRTryCatchReplaceTest : KotlinStructuralReplaceTest() {
    fun `test try catch replacement empty before after multi line`() {
        doTest(searchPattern = """
            try {
                '_TRYBLOCK*
            } finally {
                '_FINALLYBEFORE*
                println("bar")
                '_FINALLYAFTER*
            }
        """.trimIndent(), replacePattern = """
            try {
                '_TRYBLOCK
            } catch (e: Throwable) {
                foo()
            } finally {
                '_FINALLYBEFORE
                println("bar")
                '_FINALLYAFTER
            }
        """.trimIndent(), match = """
            fun main() {
                try {
                    println("foo")
                } finally {
                    println("bar")
                }
            }
        """.trimIndent(), result = """
            fun main() {
                try {
                    println("foo")
                } catch (e: Throwable) {
                    foo()
                } finally {
                    println("bar")
                }
            }
        """.trimIndent(), reformat = true)
    }

    fun `test try catch replacement empty before after same line`() {
        doTest(searchPattern = """
            try {
                '_TRYBLOCK*
            } finally {
                '_FINALLYBEFORE*
                println("bar")
                '_FINALLYAFTER*
            }
        """.trimIndent(), replacePattern = """
            try {
                '_TRYBLOCK
            } catch (e: Throwable) {
                foo()
            } finally {
                '_FINALLYBEFORE println("bar") '_FINALLYAFTER
            }
        """.trimIndent(), match = """
            fun main() {
                try {
                    println("foo")
                } finally {
                    println("bar")
                }
            }
        """.trimIndent(), result = """
            fun main() {
                try {
                    println("foo")
                } catch (e: Throwable) {
                    foo()
                } finally {
                    println("bar")
                }
            }
        """.trimIndent(), reformat = true)
    }

    fun `test try catch replacement filled before after`() {
        doTest(searchPattern = """
            try {
                '_TRYBLOCK*
            } finally {
                '_FINALLYBEFORE*
                println("bar")
                '_FINALLYAFTER*
            }
        """.trimIndent(), replacePattern = """
            try {
                '_TRYBLOCK
            } catch (e: Throwable) {
                foo()
            } finally {
                '_FINALLYBEFORE
                println("bar")
                '_FINALLYAFTER
            }
        """.trimIndent(), match = """
            fun main() {
                try {
                    println("foo")
                } finally {
                    println("fooBar")
                    println("bar")
                    println("barFoo")
                }
            }
        """.trimIndent(), result = """
            fun main() {
                try {
                    println("foo")
                } catch (e: Throwable) {
                    foo()
                } finally {
                    println("fooBar")
                    println("bar")
                    println("barFoo")
                }
            }
        """.trimIndent(), reformat = true)
    }
}