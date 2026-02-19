// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.test

import org.jetbrains.kotlin.idea.completion.CompletionBindingContextProvider
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.test.utils.withExtension

abstract class AbstractK1CompletionIncrementalResolveTest : AbstractCompletionIncrementalResolveTest() {
    override fun doTest(testPath: String) {
        require(CompletionBindingContextProvider.ENABLED) {
            "This test expects ${CompletionBindingContextProvider::class.simpleName} to be enabled"
        }

        val testLog = StringBuilder()
        CompletionBindingContextProvider.getInstance(project).TEST_LOG = testLog

        super.doTest(testPath)

        KotlinTestUtils.assertEqualsToFile(dataFile().withExtension(".log"), testLog.toString())
    }
}