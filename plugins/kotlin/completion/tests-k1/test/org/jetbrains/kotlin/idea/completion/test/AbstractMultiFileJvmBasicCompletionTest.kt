// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.test

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.kotlin.idea.test.AstAccessControl
import org.jetbrains.kotlin.idea.test.CompilerTestDirectives
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import java.io.File
import kotlin.io.path.Path

abstract class AbstractMultiFileJvmBasicCompletionTest : KotlinCompletionTestCase() {
    protected open fun doTest(testPath: String) {
        val fileName = getTestName(false) + ".kt"
        val filePath = Path(testDataDirectory.path, fileName)
        configureByFile(fileName, "")
        val shouldFail = testPath.contains("NoSpecifiedType")
        val fileText = file.text

        IgnoreTests.runTestIfNotDisabledByFileDirective(filePath, IgnoreTests.DIRECTIVES.IGNORE_K1) {
            withCustomCompilerOptions(fileText, project, module) {
                AstAccessControl.testWithControlledAccessToAst(shouldFail, file.virtualFile, project, testRootDisposable) {
                    testCompletion(
                      fileText = fileText,
                      platform = JvmPlatforms.unspecifiedJvmPlatform,
                      complete = { completionType, invocationCount ->
                          setType(completionType)
                          complete(invocationCount)
                          myItems
                      },
                      defaultCompletionType = CompletionType.BASIC,
                      defaultInvocationCount = 0,
                      additionalValidDirectives = CompilerTestDirectives.ALL_COMPILER_TEST_DIRECTIVES + IgnoreTests.DIRECTIVES.IGNORE_K2,
                    )
                }
            }
        }
    }

    override fun getTestDataDirectory() = File(KotlinTestUtils.getTestsRoot(this::class.java), getTestName(false))
}