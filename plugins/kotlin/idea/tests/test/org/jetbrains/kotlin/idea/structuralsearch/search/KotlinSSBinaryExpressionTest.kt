// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSResourceInspectionTest

class KotlinSSBinaryExpressionTest : KotlinSSResourceInspectionTest() {
    override fun getBasePath(): String = "binaryExpression"

    fun testBinaryExpression() { doTest("1 + 2 - 3") }

    fun testBinaryParExpression() { doTest("3 * (2 - 3)") }

    fun testTwoBinaryExpressions() { doTest("a = 1 \n b = 2") }

    fun testBinarySameVariable() { doTest("'_x + '_x") }

    fun testBinaryPlus() { doTest("1 + 2") }

    fun testBinaryMinus() { doTest("1 - 2") }

    fun testBinaryTimes() { doTest("1 * 2") }

    fun testBinaryDiv() { doTest("1 / 2") }

    fun testBinaryRem() { doTest("1 % 2") }

    fun testBinaryRangeTo() { doTest("1..2") }

    fun testBinaryIn() { doTest("1 in 0..2") }

    fun testBinaryNotIn() { doTest("1 !in 0..2") }

    fun testBinaryBigThan() { doTest("1 > 2") }

    fun testBinaryLessThan() { doTest("1 < 2") }

    fun testBinaryBigEqThan() { doTest("1 >= 2") }

    fun testBinaryLessEqThan() { doTest("1 <= 2") }

    fun testBinaryEquality() { doTest("a == b") }

    fun testBinaryInEquality() { doTest("a != b") }

    fun testElvis() { doTest("'_ ?: '_") }

    fun testBinaryPlusAssign() { doTest("'_ += '_") }

    fun testBinaryAssignPlus() { doTest("'_x = '_x + '_") }

    fun testBinaryMinusAssign() { doTest("'_ -= '_") }

    fun testBinaryAssignMinus() { doTest("'_x = '_x - '_") }

    fun testBinaryTimesAssign() { doTest("'_ *= '_") }

    fun testBinaryAssignTimes() { doTest("'_x = '_x * '_") }

    fun testBinaryDivAssign() { doTest("'_ /= '_") }

    fun testBinaryAssignDiv() { doTest("'_x = '_x / '_") }

    fun testBinaryRemAssign() { doTest("'_ %= '_") }

    fun testBinaryAssignRem() { doTest("'_x = '_x % '_") }

    fun testBinarySet() { doTest("a[0] = 1 + 2") }
}