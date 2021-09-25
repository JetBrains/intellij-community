// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.filters

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSResourceInspectionTest

class KotlinSSCustomFilterTests: KotlinSSResourceInspectionTest() {
    override fun getBasePath(): String = "customFilter"

    private val enabled = OneStateFilter.ENABLED

    fun testVarOnlyFilter() { doTest("var '_:[_${VarOnlyFilter.CONSTRAINT_NAME}($enabled)]") }

    fun testValOnlyFilter() { doTest("val '_:[_${ValOnlyFilter.CONSTRAINT_NAME}($enabled)]") }

}