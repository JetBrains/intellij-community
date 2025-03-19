// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.test.handlers

import com.intellij.codeInsight.lookup.LookupElementPresentation
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.nio.file.Paths

@RunWith(JUnit38ClassRunner::class)
class K1CompletionMultiFileHandlerTest : AbstractCompletionMultiFileHandlerTest() {

    override fun doTest(
        completionChar: Char,
        vararg extraFileNames: String,
        predicate: LookupElementPresentation.() -> Boolean,
    ) {
        val testFile = Paths.get(testDataDirectory.path, getTestFileName())

        IgnoreTests.runTestIfNotDisabledByFileDirective(testFile, IgnoreTests.DIRECTIVES.IGNORE_K1) {
            super.doTest(
                completionChar = completionChar,
                extraFileNames = extraFileNames,
                predicate = predicate,
            )
        }
    }
}