// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.completion.test.handlers

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractCompletionMultiFileHandlerTest
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.test.utils.IgnoreTests
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.nio.file.Paths

@RunWith(JUnit38ClassRunner::class)
class K2CompletionMultiFileHandlerTest : AbstractCompletionMultiFileHandlerTest() {
    override fun isFirPlugin(): Boolean = true

    /**
     * This is a temporary solution! This test should be rewritten to be generated!
     */
    override fun doTest(completionChar: Char, vararg extraFileNames: String, tailText: String?) {
        val testFile = Paths.get(testDataDirectory.path, getTestFileName())

        IgnoreTests.runTestIfNotDisabledByFileDirective(testFile, IgnoreTests.DIRECTIVES.IGNORE_K2) {
            super.doTest(completionChar, *extraFileNames, tailText = tailText)
        }
    }

    override val captureExceptions: Boolean = false

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
}
