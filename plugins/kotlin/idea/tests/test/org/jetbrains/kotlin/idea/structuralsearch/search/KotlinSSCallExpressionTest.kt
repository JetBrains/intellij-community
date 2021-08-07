// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSResourceInspectionTest

class KotlinSSCallExpressionTest : KotlinSSResourceInspectionTest() {
    override fun getBasePath(): String = "callExpression"

    fun testConstrArgCall() { doTest("A(true, 0, 1)") }

    fun testConstrCall() { doTest("A()") }

    fun testConstrLambdaArgCall() { doTest("A { println() }") }

    fun testConstrMixedSpreadVarargCall() { doTest("A(0, 1, 2, 3, 4)") }

    fun testConstrMixedVarargCall() { doTest("A(0, *intArrayOf(1, 2, 3), 4)") }

    fun testConstrNamedArgsCall() { doTest("A(b = true, c = 0, d = 1)") }

    fun testConstrSpreadVarargCall() { doTest("A(1, 2, 3)") }

    fun testConstrTypeArgCall() { doTest("A<Int, String>(0, \"a\")") }

    fun testConstrVarargCall() { doTest("A(*intArrayOf(1, 2, 3))") }

    fun testFunArgCall() { doTest("a(true, 0)") }

    fun testFunArgCallVarRef() { doTest("'_('_)") }

    fun testFunCall() { doTest("'_()") }

    fun testFunCallDefaultArg() { doTest("a('_)") }

    fun testFunCallDefaultArgValue() { doTest("a(0)") }

    fun testFunCallDefaultVararg() { doTest("a('_*)") }

    fun testFunCallDefaultArgMixed() { doTest("a('_{5,5})") }

    fun testFunCallDefaultTrailingLambda() { doTest("a('_)") }

    fun testFunCallNamedDefaultArg() { doTest("a('_, '_)") }

    fun testFunExtensionCall() { doTest("0.a()") }

    fun testFunLambdaArgCall() { doTest("a { println() }") }

    fun testFunMixedArgsCall() { doTest("a(c = 0, b = true)") }

    fun testFunMixedSpreadVarargCall() { doTest("a(0, 1, 2, 3, 4)") }

    fun testFunMixedVarargCall() { doTest("a(0, *intArrayOf(1, 2, 3), 4)") }

    fun testFunNamedArgsCall() { doTest("a(b = true, c = 0)") }

    fun testFunSpreadVarargCall() { doTest("a(1, 2, 3)") }

    fun testFunTypeArgCall() { doTest("a<Int, String>(0, \"a\")") }

    fun testFunVarargCall() { doTest("a(*intArrayOf(1, 2, 3))") }

    fun testLambdaCallInvoke() { doTest("a()") }

    fun testLambdaCallInvokeArgs() { doTest("a(0, 0)") }

    fun testCallAnyParameter() { doTest("'_('_*)") }

    fun testFunTrailingLambda() { doTest("'_('_+)") }

    fun testFunTrailingLambdaMultiArg() { doTest("'_('_{2,2})") }

    fun testFqCallExpression() { doTest("A.B()") }
}