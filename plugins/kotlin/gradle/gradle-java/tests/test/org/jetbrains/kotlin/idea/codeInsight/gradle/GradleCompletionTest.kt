// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test

class GradleCompletionTest : MultiplePluginVersionGradleImportingCodeInsightTestCase() {
    override fun testDataDirName() = "completion"

    private fun doCompletionTest(expected: List<String>) {
        val files = configureByFiles()
        importProject()

        val mainFile = files.find { it.nameWithoutExtension == "mainFile" } ?: error("mainFile is not found")
        codeInsightTestFixture.configureFromExistingVirtualFile(mainFile)
        runInEdtAndWait {
            codeInsightTestFixture.performEditorAction(IdeActions.ACTION_CODE_COMPLETION)
            val actual = codeInsightTestFixture.lookupElementStrings
            assertEquals(
                expected,
                actual,
            )
        }
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testExpectAndActualDuplicationFunction() = doCompletionTest(listOf("myFunctionToCheck", "anotherMyFunctionToCheck"))

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testExpectAndActualDuplicationClass() = doCompletionTest(listOf("ClassToCheck", "AnotherClassToCheck"))
}