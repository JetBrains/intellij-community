// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structuralsearch.modifiers

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSResourceInspectionTest

class KotlinSSCustomModifierTests: KotlinSSResourceInspectionTest() {
    override fun getBasePath(): String = "customModifier"

    private val enabled = OneStateModifier.ENABLED

    fun testVarOnlyModifier() { doTest("var '_:[_${VarOnlyModifier.CONSTRAINT_NAME}($enabled)]") }

    fun testValOnlyModifier() { doTest("val '_:[_${ValOnlyModifier.CONSTRAINT_NAME}($enabled)]") }
}