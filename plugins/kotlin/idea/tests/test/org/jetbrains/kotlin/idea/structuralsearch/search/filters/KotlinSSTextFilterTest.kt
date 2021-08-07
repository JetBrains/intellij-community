// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search.filters

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSResourceInspectionTest

class KotlinSSTextFilterTest : KotlinSSResourceInspectionTest() {
    override fun getBasePath(): String = "textFilter"

    fun testHierarchyClassName() { doTest("class '_:*[regex(Foo2)]") }

    fun testHierarchyClassDeclaration() { doTest("class Foo2 { val '_:*[regex(.*)] }") }

    fun testHierarchyClassSuperType() { doTest("class '_ : '_:*[regex(Foo2)]()") }

    fun testFqSuperType() { doTest("class '_ : '_:[regex(test\\.Foo)]()") }

    fun testFqTypeAlias() { doTest("fun '_('_ : '_:[regex(test\\.OtherInt)])") }

    fun testFqClassName() { doTest("class '_:[regex(test\\.A)]") }
}