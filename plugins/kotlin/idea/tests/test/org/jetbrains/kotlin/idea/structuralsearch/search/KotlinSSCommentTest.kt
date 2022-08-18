// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSCommentTest : KotlinStructuralSearchTest() {
    override fun getBasePath() = "comment"

    /**
     * EOL
     */

    fun testEol() { doTest("//") }

    fun testEolBeforeProperty() { doTest("""
        //
        val '_ = '_
    """.trimIndent()) }

    fun testEolBeforeClass() { doTest("""
        //
        class '_
    """.trimIndent()) }

    fun testEolInProperty() { doTest("val '_ = '_ //") }

    /**
     * Block
     */

    fun testBlock() { doTest("/**/") }

    fun testRegex() { doTest("// '_a:[regex( bar. )] = '_b:[regex( foo. )]") }

    /**
     * KDoc
     */

    fun testKDoc() { doTest("""
        /**
         *
         */
    """.trimIndent()) }

    fun testKDocTag() { doTest("""
        /**
         * @'_ '_
         */
    """.trimIndent()) }

    fun testKDocClass() { doTest("""
        /**
         *
         */
        class '_
    """.trimIndent()) }

    fun testKDocProperty() { doTest("""
        /**
         *
         */
        val '_ = '_
    """.trimIndent()) }

}