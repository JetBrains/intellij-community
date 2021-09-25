// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSResourceInspectionTest

class KotlinSSDoubleColonExpressionTest : KotlinSSResourceInspectionTest() {
    override fun getBasePath(): String = "doubleColonExpression"

    fun testClassLiteralExpression() { doTest("Int::class") }

    fun testFqClassLiteralExpression() { doTest("kotlin.Int::class") }

    fun testDotQualifiedExpression() { doTest("Int::class.java") }
}