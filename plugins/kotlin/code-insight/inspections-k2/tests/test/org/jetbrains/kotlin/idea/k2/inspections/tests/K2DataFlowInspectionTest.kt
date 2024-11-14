// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.inspections.tests

import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.k2.codeinsight.inspections.dfa.KotlinConstantConditionsInspection
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.test.TestMetadata

@TestRoot("idea/tests")
@TestMetadata("testData/inspections/dfa")
class K2DataFlowInspectionTest : AbstractK2InspectionTest() {
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
    fun testBrokenCodeK2() = doTest()
    fun testCallWithSideEffect() = doTest()
    fun testCastArray() = doTest()
    fun testCastGenericMethodReturn() = doTest()
    fun testCharExtension() = doTest()
    fun testClassRef() = doTest()
    fun testCollectionConstructors() = doTest()
    fun testCompareInLoop() = doTest()
    fun testComparison() = doTest()
    fun testComparisonNoValues() = doTest(false)
    fun testConstantDivisionByZero() = doTest()
    fun testConstantWithDifferentType() = doTest()
    fun testCustomObjectComparisonK2() = doTest()
    fun testDestructuringInLoop() = doTest()
    fun testDoubleComparison() = doTest()
    fun testEnumComparison() = doTest()
    fun testEnumOrdinal() = doTest()
    fun testExclamationK2() = doTest()
    fun testExtensionImplicitThis() = doTest()
    fun testFieldAliasing() = doTest()
    fun testForLoop() = doTest()
    fun testInRange() = doTest()
    fun testInIterable() = doTest()
    fun testIncompleteCode1K2() = doTest()
    fun testInlineClass() = doTest()
    fun testInlineLambda() = doTest()
    fun testInlineStandardCalls() = doTest()
    fun testIndices() = doTest()
    fun testJavaFields() {
        myFixture.addClass("""
            public class Point {
                public int x, y;
            }""".trimIndent()
        )
        doTest()
    }
    fun testJavaMethods() = doTest()
    fun testJavaConstant() = doTest()
    fun testJavaType() = doTest()
    fun testLambda() = doTest()
    fun testLanguageConstructs() = doTest()
    fun testLastIndex() = doTest()
    fun testLetNonLocalReturn() = doTest()
    fun testList() = doTest()
    fun testListApply() = doTest()
    fun testMapEmpty() = doTest()
    fun testMath() = doTest()
    fun testMembers() = doTest()
    fun testNestedLoopLabel() = doTest()
    fun testNestedThis() = doTest()
    fun testNothingType() = doTest()
    fun testPlatformType() {
        // KTIJ-22430
        myFixture.addClass("""
            public class SomeJavaUtil {
                public static Boolean b() {
                    return false;
                }
            }""".trimIndent()
        )
        doTest()
    }
    fun testPrimitiveAndNullK2() = doTest()
    fun testProperty() = doTest()
    fun testQualifierK2() = doTest()
    fun testRangeAnnotation() = doTest()
    fun testReifiedGenericK2() = doTest()
    fun testReturnContractK2() = doTest()
    fun testSingleton() = doTest()
    fun testSmartCastConflictK2() = doTest()
    fun testSmartCastExtensionCondition() = doTest()
    fun testSmartCastWhenK2() = doTest()
    fun testStaticAnalysisVsHumanBrain() = doTest()
    fun testStringComparison() = doTest()
    fun testStringTemplate() = doTest()
    fun testStrings() = doTest()
    fun testSuppressionsK2() = doTest()
    fun testTopLevelDeclaration() = doTest()
    fun testTryCatch() = doTest()
    fun testTryCatchReturnValue() = doTest()
    fun testTryCatchInsideFinally() = doTest()
    fun testTryFinally() = doTest()
    fun testTypeCastK2() = doTest()
    fun testTypeTestK2() = doTest()
    fun testUInt() = doTest()
    fun testUsefulNullK2() = doTest()
    fun testWhenToDo() = doTest()
    fun testWhenK2() = doTest()
    fun testWhenInLambdaK2() = doTest()
    fun testWhenIsObject() = doTest()
    fun testWhenGuarded() = doTest()
    fun testWhileLoop() = doTest()

    fun doTest(warnOnConstantRefs: Boolean = true) {
        val fileName = "${getTestName(false)}.kt"
        (myFixture as CodeInsightTestFixtureImpl).setVirtualFileFilter { vFile ->
            if (vFile == file.virtualFile) return@setVirtualFileFilter false
            // LightClassUtil.toLightMethods triggers loading of some annotation classes from Kotlin standard library, including
            // kotlin.SinceKotlin, or kotlin.annotation.Target.
            // It goes through it.navigationElement inside org.jetbrains.kotlin.asJava.LightClassUtil.getPsiMethodWrappers
            // then hundreds of frames and eventually ends up in PsiRawFirBuilder.Visitor.toFirConstructor where getConstructorKeyword
            // is called, which in turn causes tree loading.
            // See KT-66400 for details.
            val fromLightClassUtil = StackWalker.getInstance().walk { stream -> stream.anyMatch { ste ->
                ste.className == "org.jetbrains.kotlin.asJava.LightClassUtilsKt" &&
                ste.methodName == "toLightMethods"
            } }
            !fromLightClassUtil
        }
        myFixture.configureByFile(fileName)
        withCustomCompilerOptions(file.text, project, module) {
            myFixture.enableInspections(KotlinConstantConditionsInspection().also { it.warnOnConstantRefs = warnOnConstantRefs })
            myFixture.testHighlighting(true, false, true, fileName)
        }
    }
}