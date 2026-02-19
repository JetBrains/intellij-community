// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSConstantExpressionTest : KotlinStructuralSearchTest() {
    fun testNull() { doTest("null", "val a = <warning descr=\"SSR\">null</warning>") }

    fun testBoolean() { doTest("true", "val a = <warning descr=\"SSR\">true</warning>") }

    fun testInteger() { doTest("1", "val a = <warning descr=\"SSR\">1</warning>") }

    fun testFloat() { doTest("1.0f", "val a = <warning descr=\"SSR\">1.0f</warning>") }

    fun testCharacter() { doTest("'a'", "val a = <warning descr=\"SSR\">'a'</warning>") }
}