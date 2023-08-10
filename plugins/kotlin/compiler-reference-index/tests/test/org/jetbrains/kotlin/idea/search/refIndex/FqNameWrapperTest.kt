// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.search.refIndex

import junit.framework.TestCase
import org.jetbrains.kotlin.name.FqName

class FqNameWrapperTest : TestCase() {
    private val dollar get() = '$'

    fun `test root package`(): Unit = doTest("Main", "Main")
    fun `test root non-upper bound case`(): Unit = doTest("main", "main")

    fun `test simple`(): Unit = doTest("one.two.three.Main", "one.two.three.Main")
    fun `test nested`(): Unit = doTest("one.two.three.Main.Nested", "one.two.three.Main${dollar}Nested")
    fun `test nested nested`(): Unit = doTest("one.two.three.Main.Nested.T", "one.two.three.Main${dollar}Nested${dollar}T")

    private fun doTest(fqNameAsString: String, jvmFqName: String) {
        val fqName = FqName(fqNameAsString)

        val fromFqName = FqNameWrapper.createFromFqName(fqName)
        assertEquals(jvmFqName, fromFqName.jvmFqName)

        val fromJvmFqName = FqNameWrapper.createFromJvmFqName(jvmFqName)
        assertEquals(fqName, fromJvmFqName.fqName)
    }
}