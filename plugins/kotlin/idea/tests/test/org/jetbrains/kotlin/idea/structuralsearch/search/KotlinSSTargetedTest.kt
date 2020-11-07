/*
 * Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSResourceInspectionTest

class KotlinSSTargetedTest : KotlinSSResourceInspectionTest() {
    override fun getBasePath(): String = "targeted"

    fun testTargetAnyField() {
        doTest(
            """
                class '_Class {  
                    val 'Field+ = '_Init?
                }
            """.trimIndent()
        )
    }

    fun testTargetSpecificField() {
        doTest(
            """
                class '_Class {  
                    val 'Field+ = 45
                }
            """.trimIndent()
        )
    }
}