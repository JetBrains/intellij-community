// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.test.handlers

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.internal.runners.JUnit38ClassRunner
import org.jetbrains.kotlin.test.utils.IgnoreTests
import org.junit.runner.RunWith
import java.nio.file.Paths

@RunWith(JUnit38ClassRunner::class)
class HighLevelCompletionMultifileHandlerTest : CompletionMultiFileHandlerTest() {

    /**
     * This is a temporary solution! This test should be rewritten to be generated!
     */
    override fun doTest(completionChar: Char, vararg extraFileNames: String, tailText: String?) {
        val testFile = Paths.get(testDataPath, "${getTestName(false)}-1.kt")

        IgnoreTests.runTestIfEnabledByFileDirective(testFile, IgnoreTests.DIRECTIVES.FIR_COMPARISON) {
            super.doTest(completionChar, *extraFileNames, tailText = tailText)
        }
    }

    override val captureExceptions: Boolean = false

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE
}
