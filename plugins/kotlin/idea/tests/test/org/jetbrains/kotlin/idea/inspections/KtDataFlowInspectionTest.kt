// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections

import org.jetbrains.kotlin.idea.inspections.dfa.KotlinConstantConditionsInspection
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.TestRoot
import org.jetbrains.kotlin.test.TestMetadata

@TestRoot("idea/tests")
@TestMetadata("testData/inspections/dfa")
class KtDataFlowInspectionTest : KotlinLightCodeInsightFixtureTestCase() {
    fun testAlwaysZero() = doTest()
    fun testArrays() = doTest()
    fun testBoolean() = doTest()
    fun testBooleanConst() = doTest()
    fun testBoxedInt() = doTest()
    fun testBrokenCode() = doTest()
    fun testCastGenericMethodReturn() = doTest()
    fun testClassRef() = doTest()
    fun testCollectionConstructors() = doTest()
    fun testComparison() = doTest()
    fun testCustomObjectComparison() = doTest()
    fun testDestructuringInLoop() = doTest()
    fun testDoubleComparison() = doTest()
    fun testEnumComparison() = doTest()
    fun testExclamation() = doTest()
    fun testForLoop() = doTest()
    fun testInRange() = doTest()
    fun testIncompleteCode1() = doTest()
    fun testInlineLambda() = doTest()
    fun testInlineStandardCalls() = doTest()
    fun testJavaMethods() = doTest()
    fun testLambda() = doTest()
    fun testLanguageConstructs() = doTest()
    fun testList() = doTest()
    fun testMath() = doTest()
    fun testNothingType() = doTest()
    fun testProperty() = doTest()
    fun testQualifier() = doTest()
    fun testSmartCastConflict() = doTest()
    fun testStaticAnalysisVsHumanBrain() = doTest()
    fun testStringComparison() = doTest()
    fun testStringTemplate() = doTest()
    fun testStrings() = doTest()
    fun testSuppressions() = doTest()
    fun testTryCatch() = doTest()
    fun testTryCatchInsideFinally() = doTest()
    fun testTryFinally() = doTest()
    fun testTypeCast() = doTest()
    fun testTypeTest() = doTest()
    fun testWhen() = doTest()
    fun testWhileLoop() = doTest()

    fun doTest() {
        val fileName = "${getTestName(false)}.kt"
        myFixture.configureByFile(fileName)
        myFixture.enableInspections(KotlinConstantConditionsInspection())
        myFixture.testHighlighting(true, false, true, fileName)
    }
}