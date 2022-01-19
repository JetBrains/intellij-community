// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.structuralsearch.search.filters

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSResourceInspectionTest
import org.jetbrains.kotlin.idea.structuralsearch.filters.AlsoMatchValModifier
import org.jetbrains.kotlin.idea.structuralsearch.filters.AlsoMatchVarModifier
import org.jetbrains.kotlin.idea.structuralsearch.filters.OneStateFilter

class KotlinSSValVarModifierTests: KotlinSSResourceInspectionTest() {
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