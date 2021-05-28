// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.test.handlers

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.internal.runners.JUnit38ClassRunner
import org.jetbrains.kotlin.test.uitls.IgnoreTests
import org.junit.runner.RunWith
import java.nio.file.Paths

@RunWith(JUnit3WithIdeaConfigurationRunner::class)
class HighLevelCompletionMultifileHandlerTest : CompletionMultiFileHandlerTest() {

    /**
     * This is a temporary solution! This test should be rewritten to be generated!
     */
    override fun doTest(fileName: String, completionChar: Char, extraFileNames: Array<out String>, tailText: String?) {
        val testFile = Paths.get(testDataPath, "$fileName-1.kt")

        IgnoreTests.runTestIfEnabledByFileDirective(testFile, IgnoreTests.DIRECTIVES.FIR_COMPARISON) {
            super.doTest(fileName, completionChar, extraFileNames, tailText)
        }
    }

    override val captureExceptions: Boolean = false

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.INSTANCE
}
