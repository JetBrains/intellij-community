/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.completion.test

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.projectRoots.Sdk
import org.jetbrains.kotlin.idea.test.AstAccessControl
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.test.KotlinTestUtils
import java.io.File

abstract class AbstractMultiFileJvmBasicCompletionTest : KotlinCompletionTestCase() {
    override fun getTestProjectJdk(): Sdk? = null

    protected fun doTest(testPath: String) {
        configureByFile(getTestName(false) + ".kt", "")
        val shouldFail = testPath.contains("NoSpecifiedType")
        AstAccessControl.testWithControlledAccessToAst(shouldFail, file.virtualFile, project, testRootDisposable) {
            testCompletion(
                fileText = file.text,
                platform = JvmPlatforms.unspecifiedJvmPlatform,
                complete = { completionType, invocationCount ->
                    setType(completionType)
                    complete(invocationCount)
                    myItems
                },
                defaultCompletionType = CompletionType.BASIC,
                defaultInvocationCount = 0,
            )
        }
    }

    override fun getTestDataDirectory() = File(KotlinTestUtils.getTestsRoot(this::class.java), getTestName(false))
}