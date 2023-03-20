// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinStructuralSearchTest

class KotlinSSLoopStatementTest : KotlinStructuralSearchTest() {
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