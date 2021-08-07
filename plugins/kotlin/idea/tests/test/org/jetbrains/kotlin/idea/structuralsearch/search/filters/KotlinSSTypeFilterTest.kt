// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search.filters

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSResourceInspectionTest
import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchProfile

class KotlinSSTypeFilterTest : KotlinSSResourceInspectionTest() {

    override fun getBasePath(): String = "typeFilter"

    // Behavior

    fun testShortNameTypeFilter() { doTest("val '_x:[exprtype(Int)]") }

    fun testFqNameTypeFilter() { doTest("val '_x:[exprtype(A.B.Foo)]") }

    fun testWithinHierarchyTypeFilter() { doTest("val '_x:[exprtype(*Number)]") }

    fun testNullableType() { doTest("'_('_:[exprtype(Int?)])") }

    fun testNullableTypeHierarchy() { doTest("val '_:[exprtype(*A)]") }
    
    fun testNullableFunctionType() { doTest("'_('_:[exprtype(\\(\\(\\) -> Unit\\)?)])") }
    
    fun testNull() { doTest("'_('_:[exprtype(null)])") }

    fun testArgs() { doTest("val '_:[exprtype(Array<Int>)]") }

    fun testFunctionType() { doTest("val '_:[exprtype( (String) -> Int )]") }

    fun testFunctionType2() { doTest("val '_:[exprtype( (String, Int) -> Boolean )]") }

    fun testFunctionType3() { doTest("val '_:[exprtype( () -> Unit )]") }

    fun testInVariance() { doTest("fun '_('_:[exprtype(Array<in String>)])") }

    fun testOutVariance() { doTest("fun '_('_:[exprtype(Array<out Any>)])") }

    fun testFunctionTypeReceiver() { doTest("val '_ = '_:[exprtype(TestClass.\\(\\) -> Unit)]") }
    
    fun testSuspendFunctionType() { doTest("val '_ = '_:[exprtype(suspend \\(\\) -> Unit)]") }

    fun testFunctionTypeSupertype() { doTest("val '_:[exprtype(*\\(\\) -> Unit)]") }

    fun testStarProjection() { doTest("fun '_('_ : Foo<*>)") }
    
    // Elements where type filter is enabled

    fun testTypeValueArgument() { doTest("'_('_:[exprtype(String)])") }

    fun testTypeBinaryExpression() { doTest("'_:[exprtype(Int)] + '_:[exprtype(Float)]") }

    fun testTypeBinaryExpressionWithTypeRHS() { doTest("'_:[exprtype(Any)] as '_") }

    fun testTypeIsExpression() { doTest("'_:[exprtype(Any)] is '_") }

    fun testTypeBlockExpression() { doTest("{'_ -> '_:[exprtype(Int)]}") }

    fun testTypeArrayAccessArrayExpression() { doTest("'_:[exprtype(Array)]['_]") }

    fun testTypeArrayAccessIndicesNode() { doTest("'_['_:[exprtype(String)]]") }

    fun testTypePostfixExpression() { doTest("'_:[exprtype(Int)]++") }

    fun testTypeDotQualifiedExpression() { doTest("'_:[exprtype(String)].'_") }

    fun testTypeSafeQualifiedExpression() { doTest("'_:[exprtype(A?)]?.'_") }

    fun testTypeCallableReferenceExpression() { doTest("'_:[exprtype(A)]::'_") }

    fun testTypeSimpleNameStringTemplateEntry() { doTest(""" "$$'_:[exprtype(Int)]" """) }

    fun testTypeBlockStringTemplateEntry() { doTest(""" "${'$'}{ '_:[exprtype(Int)] }" """) }

    fun testTypePropertyAccessor() { doTest("val '_ get() = '_:[exprtype(Int)]", KotlinStructuralSearchProfile.PROPERTY_CONTEXT) }

    fun testTypeWhenEntry() { doTest("when { '_ -> '_:[exprtype(Int)] }") }
}