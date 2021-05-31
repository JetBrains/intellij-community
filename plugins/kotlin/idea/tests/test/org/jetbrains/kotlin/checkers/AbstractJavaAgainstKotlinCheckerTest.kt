// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.checkers

import org.jetbrains.kotlin.idea.perf.forceUsingOldLightClassesForTest
import java.io.File

abstract class AbstractJavaAgainstKotlinSourceCheckerTest : AbstractJavaAgainstKotlinCheckerTest() {
    fun doTest(path: String) {
        val relativePath = File(path).toRelativeString(File(testDataPath))
        doTest(true, true, relativePath.replace(".kt", ".java"), relativePath)
    }
}

abstract class AbstractJavaAgainstKotlinSourceCheckerWithoutUltraLightTest : AbstractJavaAgainstKotlinSourceCheckerTest() {
    override fun setUp() {
        super.setUp()
        forceUsingOldLightClassesForTest()
    }
}
