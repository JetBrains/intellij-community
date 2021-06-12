/*
 * Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSResourceInspectionTest

class KotlinSSLoopStatementTest : KotlinSSResourceInspectionTest() {
    override fun getBasePath(): String = "loopStatement"

    fun testForLoop() {
        doTest(
            """
            for(i in 0..10) {
                println(i)
            }
            """
        )
    }

    fun testWhileLoop() {
        doTest(
            """
            while(true) {
                println(0)
            }
            """
        )
    }

    fun testDoWhileLoop() {
        doTest(
            """
            do {
                println(0)
            } while(true)
            """
        )
    }
    
    fun testWhileTwoStatements() { doTest("while ('_) { '_{2,2} }") }
}