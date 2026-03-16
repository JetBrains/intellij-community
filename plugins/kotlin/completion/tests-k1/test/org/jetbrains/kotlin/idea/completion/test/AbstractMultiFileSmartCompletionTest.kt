// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.test

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.test.AstAccessControl
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import kotlin.io.path.Path

abstract class AbstractMultiFileSmartCompletionTest : KotlinCompletionTestCase(),
                                                      ExpectedPluginModeProvider {

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
        setType(CompletionType.SMART)
    }

    protected fun doTest(unused: String) {
        val fileName = getTestName(false) + ".kt"
        val filePath = Path(testDataDirectory.path, fileName)

        IgnoreTests.runTestIfNotDisabledByFileDirective(filePath, IgnoreTests.DIRECTIVES.IGNORE_K1) {
            configureByFile(fileName, "")
            AstAccessControl.testWithControlledAccessToAst(false, file.virtualFile, project, testRootDisposable) {
                testCompletion(
                    fileText = file.text,
                    platform = JvmPlatforms.unspecifiedJvmPlatform,
                    complete = { completionType, invocationCount ->
                        setType(completionType)
                        complete(invocationCount)
                        myItems
                    },
                    defaultCompletionType = CompletionType.SMART,
                    defaultInvocationCount = 1,
                )
            }
        }
    }

    override fun getTestDataDirectory() = COMPLETION_TEST_DATA_BASE.resolve("smartMultiFile").resolve(getTestName(false))
}