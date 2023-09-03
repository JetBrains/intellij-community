// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.quickfix

import org.jetbrains.kotlin.idea.quickfix.AbstractQuickFixMultiModuleTest
import org.jetbrains.kotlin.idea.test.findFileWithCaret
import org.jetbrains.kotlin.test.utils.IgnoreTests

abstract class AbstractHighLevelQuickFixMultiModuleTest : AbstractQuickFixMultiModuleTest() {
    override fun isFirPlugin(): Boolean = true

    override fun doQuickFixTest(dirPath: String) {
        val actionFile = project.findFileWithCaret()
        val actionFilePath = actionFile.virtualFile.toNioPath()
        IgnoreTests.runTestIfNotDisabledByFileDirective(
            actionFilePath,
            disableTestDirective = IgnoreTests.DIRECTIVES.IGNORE_K2,
        ) {
            super.doQuickFixTest(dirPath)
        }
    }
}