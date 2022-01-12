// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structuralsearch.search.modifier

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSResourceInspectionTest

class KotlinSSTextModifierTest : KotlinSSResourceInspectionTest() {
    override fun getBasePath(): String = "textModifier"

    fun testHierarchyClassName() { doTest("class '_:*[regex(Foo2)]") }

    fun testHierarchyClassDeclaration() { doTest("class Foo2 { val '_:*[regex(.*)] }") }

    fun testHierarchyClassSuperType() { doTest("class '_ : '_:*[regex(Foo2)]()") }

    fun testFqSuperType() { doTest("class '_ : '_:[regex(test\\.Foo)]()") }

    fun testFqTypeAlias() { doTest("fun '_('_ : '_:[regex(test\\.OtherInt)])") }

    fun testFqClassName() { doTest("class '_:[regex(test\\.A)]") }
}