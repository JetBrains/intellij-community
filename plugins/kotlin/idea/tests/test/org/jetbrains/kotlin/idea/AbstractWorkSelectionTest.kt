// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea

import com.intellij.rt.execution.junit.FileComparisonFailure
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import java.io.File

abstract class AbstractWorkSelectionTest : KotlinLightCodeInsightFixtureTestCase() {
    protected fun doTest(testPath: String) {
        val dirName = getTestName(false)
        val dir = File(testPath)
        val filesCount = dir.listFiles()!!.size
        val afterFiles = arrayOfNulls<String>(filesCount - 1)
        for (i in 1 until filesCount) {
            afterFiles[i - 1] = dirName + File.separator + i + ".kt"
        }

        try {
            CodeInsightTestUtil.doWordSelectionTest(myFixture, dirName + File.separator + "0.kt", *afterFiles)
        } catch (error: FileComparisonFailure) {
            wrapToFileComparisonFailure(error.filePath)
        } catch (error: AssertionError) {
            val message = error.message
            val path = message!!.substring(0, message.indexOf(":"))
            wrapToFileComparisonFailure(path)
        }
    }

    override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_LATEST

    private fun wrapToFileComparisonFailure(failedFilePath: String) {
        KotlinTestUtils.assertEqualsToFile(File(failedFilePath), myFixture.editor)
    }
}