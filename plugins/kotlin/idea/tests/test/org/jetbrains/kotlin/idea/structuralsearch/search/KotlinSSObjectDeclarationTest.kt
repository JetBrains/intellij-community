// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.structuralsearch.search

import org.jetbrains.kotlin.idea.structuralsearch.KotlinSSResourceInspectionTest

class KotlinSSObjectDeclarationTest : KotlinSSResourceInspectionTest() {
    override fun getBasePath(): String = "objectDeclaration"

    fun testObject() { doTest("object '_") }

    fun testCompanionObject() { doTest("object A") }

    fun testNestedObject() { doTest("object B") }

    fun testNamedCompanionObject() {
        doTest(
            """class '_ {
                    companion object Foo { }
                }""".trimMargin()
        )
    }

    fun testNestedNamedCompanionObject() {
        doTest(
            """class '_ {
                    companion object Foo { }
                }""".trimMargin()
        )
    }
}