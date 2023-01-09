// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import junit.framework.TestCase
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class UnclearPrecedenceOfBinaryExpressionInspectionTest : KotlinLightCodeInsightFixtureTestCase() {

    fun `test elvis elvis`() = doTest("fun foo(i: Int?, j: Int?, k: Int?) = i ?: j <caret>?: k")

    fun `test elvis as`() = doTest(
        "fun foo(i: Int?, j: Int?) = i ?: j<caret> as String?",
        "fun foo(i: Int?, j: Int?) = i ?: (j as String?)"
    )

    fun `test elvis eqeq`() = doTest(
        "fun foo(a: Int?, b: Int?, c: Int?) = <warning>a ?: b<caret> == c</warning>",
        "fun foo(a: Int?, b: Int?, c: Int?) = (a ?: b) == c"
    )

    fun `test comments`() = doTest(
        "fun test(i: Int?, b: Boolean?) = <warning>b /* a */ <caret>?: /* b */ i /* c */ == /* d */ null</warning>",
        "fun test(i: Int?, b: Boolean?) = (b /* a */ ?: /* b */ i) /* c */ == /* d */ null"
    )

    fun `test quickfix is absent for same priority tokens`() = doTest("fun foo() = 1 + 2 <caret>+ 3")

    fun `test put parentheses through already presented parentheses`() = doTest(
        "fun foo(a: Int?) = a ?<caret>: (1 + 2 * 4)",
        "fun foo(a: Int?) = a ?: (1 + (2 * 4))"
    )

    fun `test obvious arithmetic is reported with reportEvenObviousCases flag is on`() = doTest(
        "fun foo() = <warning>1 + 2<caret> * 4</warning>",
        "fun foo() = 1 + (2 * 4)",
        reportEvenObviousCases = true
    )

    fun `test parentheses should be everywhere if reportEvenObviousCases flag is on`() = doTest(
        "fun foo(a: Int?) = <warning>a ?: 1 + <caret>2 * 4</warning>",
        "fun foo(a: Int?) = a ?: (1 + (2 * 4))",
        reportEvenObviousCases = true
    )

    fun `test only non obvious parentheses should be put if reportEvenObviousCases flag is off`() = doTest(
        "fun foo(a: Int?) = <warning>a ?: 1 + <caret>2 * 4</warning>",
        "fun foo(a: Int?) = a ?: (1 + 2 * 4)"
    )

    fun `test elvis is`() = doTest(
        "fun foo(a: Boolean?, b: Any) = <warning>a ?: <caret>b is Int</warning>",
        "fun foo(a: Boolean?, b: Any) = (a ?: b) is Int"
    )

    fun `test elvis plus`() = doTest(
        "fun foo(a: Int?) = <warning>a ?: <caret>1 + 2</warning>",
        "fun foo(a: Int?) = a ?: (1 + 2)"
    )

    fun `test top level presented parentheses`() = doTest(
        "fun foo() = <warning>(if (true) 1 else null) ?: <caret>1 xor 2</warning>",
        "fun foo() = (if (true) 1 else null) ?: (1 xor 2)"
    )

    fun `test eq elvis`() = doTest("fun test(i: Int?): Int { val y: Int; y = i <caret>?: 1; return y}")

    fun `test already has parentheses`() = doTest("fun foo(i: Int?) = (i <caret>?: 0) + 1")

    fun `test infixFun plus`() = doTest(
        "fun foo() = 1 xor 2 <caret>+ 8",
        "fun foo() = 1 xor (2 + 8)"
    )

    fun `test plus range`() = doTest(
        "fun foo() = 1 + <caret>2..4",
        "fun foo() = (1 + 2)..4"
    )

    fun `test braces inside braces`() = doTest(
        "fun foo() = ((1 + <caret>2))..4"
    )

    fun `test infixFun elvis`() = doTest(
        "fun foo(a: Int?) = <warning>a ?: 1 <caret>xor 2</warning>",
        "fun foo(a: Int?) = a ?: (1 xor 2)"
    )

    fun `test multiple infixFun`() = doTest(
        "fun foo() = 0 xor 10<caret> and 2"
    )

    private fun doTest(before: String, after: String? = null, reportEvenObviousCases: Boolean = false) {
        require(before.contains("<caret>"))
        val unclearPrecedenceOfBinaryExpressionInspection = UnclearPrecedenceOfBinaryExpressionInspection()
        unclearPrecedenceOfBinaryExpressionInspection.reportEvenObviousCases = reportEvenObviousCases
        myFixture.enableInspections(unclearPrecedenceOfBinaryExpressionInspection)
        try {
            myFixture.configureByText("foo.kt", before)
            myFixture.checkHighlighting(true, false, false)
            val intentionMsg = KotlinBundle.message("unclear.precedence.of.binary.expression.quickfix")
            if (after != null) {
                val intentionAction = myFixture.findSingleIntention(intentionMsg)
                myFixture.launchAction(intentionAction)
                myFixture.checkResult(after)
            } else {
                TestCase.assertTrue(myFixture.filterAvailableIntentions(intentionMsg).isEmpty())
            }
        } finally {
            myFixture.disableInspections(unclearPrecedenceOfBinaryExpressionInspection)
        }
    }

    override fun isFirPlugin(): Boolean = false
}