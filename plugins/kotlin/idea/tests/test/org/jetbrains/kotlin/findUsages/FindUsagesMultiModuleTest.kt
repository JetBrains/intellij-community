// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.findUsages

import org.jetbrains.kotlin.idea.multiplatform.setupMppProjectFromDirStructure
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit38ClassRunner::class)
open class FindUsagesMultiModuleTest : AbstractFindUsagesMultiModuleTest() {

    protected fun getTestdataFile(): File =
        File(testDataPath + getTestName(true).removePrefix("test"))

    fun testFindActualInterface() {
        doTest()
    }

    fun testFindCommonClassFromActual() {
        doTest()
    }

    fun testFindCommonFromActual() {
        doTest()
    }

    fun testFindCommonPropertyFromActual() {
        doTest()
    }

    fun testFindCommonSuperclass() {
        doTest()
    }

    fun testFindImplFromHeader() {
        doTest()
    }

    private fun doTest() {
        setupMppProjectFromDirStructure(getTestdataFile())
        doFindUsagesTest()
    }
}