/*
 * Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSResourceInspectionTest

class KotlinSSConstantExpressionTest : KotlinSSResourceInspectionTest() {
    override fun getBasePath(): String = "constantExpression"

    fun testNull() { doTest("null") }

    fun testBoolean() { doTest("true") }

    fun testInteger() { doTest("1") }

    fun testFloat() { doTest("1.0f") }

    fun testCharacter() { doTest("'a'") }
}