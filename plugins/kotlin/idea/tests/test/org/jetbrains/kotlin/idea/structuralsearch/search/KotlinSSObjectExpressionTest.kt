/*
 * Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSResourceInspectionTest

class KotlinSSObjectExpressionTest : KotlinSSResourceInspectionTest() {
    override fun getBasePath(): String = "objectExpression"

    fun testObject() {
        doTest(
            """
                fun '_() = object {
                    val c = 1
                }
            """
        )
    }

    fun testObjectAnyReturn() {
        doTest(
            """
                fun '_(): Any = object {
                    val c = 1
                }
            """
        )
    }

    fun testObjectAnonymous() {
        doTest(
            """
                private fun '_() = object {
                    val c = 1
                }
            """
        )
    }

    fun testObjectSuperType() {
        doTest(
            """
                fun '_() = object : '_() { }
            """
        )
    }
}