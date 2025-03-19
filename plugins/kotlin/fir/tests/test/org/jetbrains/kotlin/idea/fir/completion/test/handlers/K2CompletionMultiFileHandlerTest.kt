// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.completion.test.handlers

import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.completion.test.handlers.AbstractCompletionMultiFileHandlerTest
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.nio.file.Paths

@RunWith(JUnit38ClassRunner::class)
class K2CompletionMultiFileHandlerTest : AbstractCompletionMultiFileHandlerTest() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    /**
     * This is a temporary solution! This test should be rewritten to be generated!
     */
    override fun doTest(
        completionChar: Char,
        vararg extraFileNames: String,
        predicate: LookupElementPresentation.() -> Boolean,
    ) {
        val testFile = Paths.get(testDataDirectory.path, getTestFileName())

        IgnoreTests.runTestIfNotDisabledByFileDirective(testFile, IgnoreTests.DIRECTIVES.IGNORE_K2) {
            super.doTest(
                completionChar = completionChar,
                extraFileNames = extraFileNames,
                predicate = predicate,
            )
        }
    }

    override val captureExceptions: Boolean = false

    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
}
