// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSTargetedTest : KotlinStructuralSearchTest() {
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