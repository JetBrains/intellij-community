// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.completion.test.handlers

import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractBasicCompletionHandlerTest
import org.jetbrains.kotlin.test.utils.IgnoreTests

abstract class AbstractHighLevelBasicCompletionHandlerTest : AbstractBasicCompletionHandlerTest() {
    override val captureExceptions: Boolean = false

    override fun doTest(testPath: String) {
        IgnoreTests.runTestIfEnabledByFileDirective(testDataFilePath(), IgnoreTests.DIRECTIVES.FIR_COMPARISON, ".after") {
            super.doTest(testPath)
        }
    }
}