// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSResourceInspectionTest
import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchProfile
import org.jetbrains.kotlin.idea.structuralsearch.filters.AlsoMatchValModifier
import org.jetbrains.kotlin.idea.structuralsearch.filters.AlsoMatchVarModifier
import org.jetbrains.kotlin.idea.structuralsearch.filters.OneStateFilter

class KotlinSSPropertyTest : KotlinSSResourceInspectionTest() {
    override fun getBasePath(): String = "property"

    fun testVar() { doTest("var '_") }

    fun testVal() { doTest("val '_") }

    fun testValType() { doTest("val '_ : Int") }

    fun testValFqType() { doTest("val '_ : Foo.Int") }

    fun testValComplexFqType() { doTest("val '_ : '_<'_<'_, (Foo.Int) -> Int>>") }

    fun testValInitializer() { doTest("val '_ = 1") }

    fun testValReceiverType() { doTest("val '_ : ('_T) -> '_U = '_") }

    fun testVarTypeProjection() { doTest("var '_ : Comparable<'_T>") }

    fun testVarStringAssign() { doTest("var '_  = \"Hello world\"") }

    fun testVarStringAssignPar() { doTest("var '_  = (\"Hello world\")") }

    fun testVarRefAssign() { doTest("var '_  = a") }

    fun testVarNoInitializer() { doTest("var '_ = '_{0,0}") }

    fun testVarGetterModifier() {
        doTest("""
            var '_Field = '_ 
                @'_Ann get() = '_
        """, KotlinStructuralSearchProfile.PROPERTY_CONTEXT) }

    fun testVarSetterModifier() {
        doTest("""
            var '_Field = '_ 
                private set('_x) { '_* }
        """, KotlinStructuralSearchProfile.PROPERTY_CONTEXT) }

    fun testFunctionType() { doTest("val '_ : ('_{2,2}) -> Unit") }

    fun testFunctionTypeNamedParameter() { doTest("val '_ : ('_) -> '_") }

    fun testReturnTypeReference() { doTest("val '_ : ('_) -> Unit") }

    fun testNullableFunctionType() { doTest("val '_ : '_ ?") }

    fun testReceiverTypeReference() { doTest("val Int.'_ : '_") }

    fun testReceiverFqTypeReference() { doTest("val kotlin.Int.'_ : '_") }

    fun testAlsoMatchValModifier() { doTest("var '_:[_${AlsoMatchValModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})]", """
        fun main() {
            <warning descr="SSR">var x = 1</warning>
            <warning descr="SSR">val y = 1</warning>
            print(x + y)
        }
    """.trimIndent()) }

    fun testAlsoMatchVarModifier() { doTest("val '_:[_${AlsoMatchVarModifier.CONSTRAINT_NAME}(${OneStateFilter.ENABLED})]", """
        fun main() {
            <warning descr="SSR">var x = 1</warning>
            <warning descr="SSR">val y = 1</warning>
            print(x + y)
        }
    """.trimIndent()) }
}