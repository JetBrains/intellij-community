// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.test

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.idea.test.CompilerTestDirectives
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.test.utils.IgnoreTests
import java.io.File

abstract class KotlinFixtureCompletionBaseTestCase : KotlinLightCodeInsightFixtureTestCase() {
    abstract fun getPlatform(): TargetPlatform

    protected open fun complete(completionType: CompletionType, invocationCount: Int): Array<LookupElement>? =
        myFixture.complete(completionType, invocationCount)

    protected abstract fun defaultCompletionType(): CompletionType
    protected open fun defaultInvocationCount(): Int = 0

    protected open fun handleTestPath(path: String): File = File(path)
    
    open fun doTest(testPath: String) {
        val actualTestFile = handleTestPath(testPath())
        configureFixture(actualTestFile.path)

        val fileText = FileUtil.loadFile(actualTestFile, true)

        withCustomCompilerOptions(fileText, project, module) {
            assertTrue("\"<caret>\" is missing in file \"$testPath\"", fileText.contains("<caret>"))

            executeTest {
                if (ExpectedCompletionUtils.shouldRunHighlightingBeforeCompletion(fileText)) {
                    myFixture.doHighlighting()
                }
                testCompletion(
                    fileText,
                    getPlatform(),
                    { completionType, count -> complete(completionType, count) },
                    defaultCompletionType(),
                    defaultInvocationCount(),
                    ignoreProperties = ignoreProperties,
                    additionalValidDirectives = CompilerTestDirectives.ALL_COMPILER_TEST_DIRECTIVES + IgnoreTests.DIRECTIVES.FIR_IDENTICAL
                )
            }
        }
    }

    open val ignoreProperties: Collection<String> = emptyList()

    protected open fun executeTest(test: () -> Unit) {
        test()
    }

    protected open fun configureFixture(testPath: String) {
        myFixture.configureByFile(File(testPath).name)
    }
}
