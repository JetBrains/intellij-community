// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.inspections.dfa.KotlinConstantConditionsInspection
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.test.TestMetadata

@TestRoot("idea/tests")
@TestMetadata("testData/inspections/dfa")
class KtDataFlowInspectionTest : KotlinLightCodeInsightFixtureTestCase() {
    fun testAlwaysZero() = doTest()
    @Suppress("ClassExplicitlyAnnotation")
    fun testAnnotationInJava() {
        myFixture.addClass("""
            import java.lang.annotation.Annotation;

            @interface Storage {}

            public class FileStorageAnnotation implements Storage {
                @Override
                public Class<? extends Annotation> annotationType() {
                    throw new UnsupportedOperationException("Method annotationType not implemented in " + getClass());
                }
            }
        """.trimIndent())
        doTest()
    }
    fun testAnyType() = doTest()
    fun testArrays() = doTest()
    fun testAssertApply() = doTest()
    fun testBoolean() = doTest()
    fun testBooleanConst() = doTest()
    fun testBoxedInt() = doTest()
    fun testBrokenCode() = doTest()
    fun testCallWithSideEffect() = doTest()
    fun testCastArray() = doTest()
    fun testCastGenericMethodReturn() = doTest()
    fun testClassRef() = doTest()
    fun testCollectionConstructors() = doTest()
    fun testComparison() = doTest()
    fun testComparisonNoValues() = doTest(false)
    fun testCustomObjectComparison() = doTest()
    fun testDestructuringInLoop() = doTest()
    fun testDoubleComparison() = doTest()
    fun testEnumComparison() = doTest()
    fun testEnumOrdinal() = doTest()
    fun testExclamation() = doTest()
    fun testForLoop() = doTest()
    fun testInRange() = doTest()
    fun testInIterable() = doTest()
    fun testIncompleteCode1() = doTest()
    fun testInlineClass() = doTest()
    fun testInlineLambda() = doTest()
    fun testInlineStandardCalls() = doTest()
    fun testIndices() = doTest()
    fun testJavaMethods() = doTest()
    fun testJavaConstant() = doTest()
    fun testJavaType() = doTest()
    fun testLambda() = doTest()
    fun testLanguageConstructs() = doTest()
    fun testList() = doTest()
    fun testListApply() = doTest()
    fun testMapEmpty() = doTest()
    fun testMath() = doTest()
    fun testMembers() = doTest()
    fun testNestedLoopLabel() = doTest()
    fun testNothingType() = doTest()
    fun testPlatformType() {
        // KTIJ-22430
        myFixture.addClass(
            "public class SomeJavaUtil {\n" +
                    "\n" +
                    "    public static Boolean b() {\n" +
                    "        return false;\n" +
                    "    }\n" +
                    "}"
        )
        doTest()
    }
    fun testPrimitiveAndNull() = doTest()
    fun testProperty() = doTest()
    fun testQualifier() = doTest()
    fun testRangeAnnotation() = doTest()
    fun testReifiedGeneric() = doTest()
    fun testReturnContract() = doTest()
    fun testSingleton() = doTest()
    fun testSmartCastConflict() = doTest()
    fun testSmartCastExtensionCondition() = doTest()
    fun testSmartCastWhen() = doTest()
    fun testStaticAnalysisVsHumanBrain() = doTest()
    fun testStringComparison() = doTest()
    fun testStringTemplate() = doTest()
    fun testStrings() = doTest()
    fun testSuppressions() = doTest()
    fun testTopLevelDeclaration() = doTest()
    fun testTryCatch() = doTest()
    fun testTryCatchReturnValue() = doTest()
    fun testTryCatchInsideFinally() = doTest()
    fun testTryFinally() = doTest()
    fun testTypeCast() = doTest()
    fun testTypeTest() = doTest()
    fun testUInt() = doTest()
    fun testUsefulNull() = doTest()
    fun testWhen() = doTest()
    fun testWhenInLambda() = doTest()
    fun testWhenIsObject() = doTest()
    fun testWhileLoop() = doTest()

    fun doTest(warnOnConstantRefs: Boolean = true) {
        val fileName = "${getTestName(false)}.kt"
        KotlinCommonCompilerArgumentsHolder.getInstance(myFixture.project).update {
            languageVersion = "1.8" // `rangeUntil` operator
        }
        myFixture.configureByFile(fileName)
        val inspection = KotlinConstantConditionsInspection()
        inspection.warnOnConstantRefs = warnOnConstantRefs
        myFixture.enableInspections(inspection)
        myFixture.testHighlighting(true, false, true, fileName)
    }
}