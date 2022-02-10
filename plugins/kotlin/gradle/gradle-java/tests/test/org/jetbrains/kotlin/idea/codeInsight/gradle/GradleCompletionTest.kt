// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test
import kotlin.reflect.KMutableProperty0

class GradleCompletionTest : MultiplePluginVersionGradleImportingTestCase() {
    override fun testDataDirName() = "completion"
    private lateinit var codeInsightTestFixture: CodeInsightTestFixture

    final override fun setUpFixtures() {
        myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName()).fixture
        codeInsightTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myTestFixture)
        codeInsightTestFixture.setUp()
    }

    final override fun tearDownFixtures() = runAll(
        ThrowableRunnable { codeInsightTestFixture.tearDown() },
        ThrowableRunnable {
            @Suppress("UNCHECKED_CAST")
            (this::codeInsightTestFixture as KMutableProperty0<CodeInsightTestFixture?>).set(null)
        },
        ThrowableRunnable { myTestFixture = null },
    )

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testExpectAndActualDuplication() {
        val files = configureByFiles()
        importProject()

        val mainFile = files.find { it.nameWithoutExtension == "mainFile" } ?: error("mainFile is not found")
        codeInsightTestFixture.configureFromExistingVirtualFile(mainFile)
        runInEdtAndWait {
            codeInsightTestFixture.performEditorAction(IdeActions.ACTION_CODE_COMPLETION)
            val actual = codeInsightTestFixture.lookupElementStrings
            assertEquals(
                listOf("myFunctionToCheck", "anotherMyFunctionToCheck"),
                actual,
            )
        }
    }
}