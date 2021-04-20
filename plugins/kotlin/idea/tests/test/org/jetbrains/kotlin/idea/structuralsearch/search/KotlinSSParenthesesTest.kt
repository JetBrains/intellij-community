/*
 * Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSResourceInspectionTest

class KotlinSSParenthesesTest : KotlinSSResourceInspectionTest() {
    override fun getBasePath(): String = "parentheses"

    fun testPostIncr() { doTest("++(('_))") }

    fun testStringLiteral() { doTest("(((\"Hello World!\")))") }

    fun testVariableRef() { doTest("(('_))") }

    fun testBinaryExpr() { doTest("1 + 2 + 3") }
}