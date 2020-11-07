/*
 * Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.structuralsearch.replace

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSRReplaceTest

class KotlinSSRQualifiedExpressionReplaceTest : KotlinSSRReplaceTest() {
    fun testQualifiedExpressionReceiverWithCountFilter() {
        doTest(
            searchPattern = "'_BEFORE{0,1}.'_FUN()",
            replacePattern = "'_BEFORE.foo('_ARG)",
            match = """
                fun main() {
                    bar()
                }
            """.trimIndent(),
            result = """
                fun main() {
                    foo()
                }
            """.trimIndent()
        )
    }

    fun testDoubleQualifiedExpression() {
        doTest(
            searchPattern = """
                '_REC.foo = '_INIT
                '_REC.bar = '_INIT
            """.trimIndent(),
            replacePattern = """
                '_REC.fooBar = '_INIT
            """.trimIndent(),
            match = """
                fun main() {
                    x.foo = true
                    x.bar = true
                }
            """.trimIndent(),
            result = """
                fun main() {
                    x.fooBar = true
                }
            """.trimIndent()
        )
    }
}