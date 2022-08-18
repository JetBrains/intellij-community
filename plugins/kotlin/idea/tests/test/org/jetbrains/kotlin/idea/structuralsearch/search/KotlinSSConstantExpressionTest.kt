// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSConstantExpressionTest : KotlinStructuralSearchTest() {
    override fun getBasePath(): String = "constantExpression"

    fun testNull() { doTest("null") }

    fun testBoolean() { doTest("true") }

    fun testInteger() { doTest("1") }

    fun testFloat() { doTest("1.0f") }

    fun testCharacter() { doTest("'a'") }
}