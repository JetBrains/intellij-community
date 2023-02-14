// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSExpressionWithLabelTest : KotlinStructuralSearchTest() {
    override fun getBasePath(): String = "expressionWithLabel"

    fun testBreak() { doTest("break") }
    fun testBreakLabel() { doTest("break@loop") }

    fun testContinue() { doTest("continue") }
    fun testContinueLabel() { doTest("continue@loop") }
    fun testContinueLabelRegex() { doTest("continue@'_foo:[regex( foo.* )]") }

    fun testReturn() { doTest("return 1") }
    fun testReturnLabel() { doTest("return@lit") }

    fun testSuper() { doTest("super") }
    fun testSuperTypeQualifier() { doTest("super<B>") }

    fun testThis() { doTest("this") }
    fun testThisLabel() { doTest("this@A") }

}