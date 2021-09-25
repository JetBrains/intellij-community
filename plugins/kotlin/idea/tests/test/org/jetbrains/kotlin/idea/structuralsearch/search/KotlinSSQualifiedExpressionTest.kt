// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSResourceInspectionTest

class KotlinSSQualifiedExpressionTest : KotlinSSResourceInspectionTest() {
    override fun getBasePath(): String = "qualifiedExpression"

    fun testDotRegular() { doTest("'_.'_") }

    fun testSafeAccess() { doTest("\$e1\$?.'_") }

    fun testDotNoReceiver() { doTest("'_{0,0}.'_()") }
}