// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSArrayAccessExpressionTest : KotlinStructuralSearchTest() {
    override fun getBasePath(): String = "arrayAccessExpression"

    fun testConstAccess() { doTest("a[0]") }

    fun testConstAccessGet() { doTest("a[0]") }
}