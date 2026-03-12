// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeInsight.gradle

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.core.FileComparisonFailedError
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.base.test.TestRoot
import org.jetbrains.kotlin.idea.test.AssertKotlinPluginMode
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.UseK2PluginMode
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.TestMetadata
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.testFramework.fixtures.application.GradleProjectTestApplication
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.io.File
import kotlin.reflect.KMutableProperty0

@UseK2PluginMode
@GradleProjectTestApplication
@AssertKotlinPluginMode
@TestRoot("idea/tests/testData/")
@TestDataPath("\$CONTENT_ROOT")
@TestMetadata("../../../idea/tests/testData/gradle/languageFeature/")
class GradleUpdateConfigurationQuickFixTest : GradleImportingTestCase() {
    private lateinit var codeInsightTestFixture: CodeInsightTestFixture

    private fun getTestDataDirectory(): File {
        return IDEA_TEST_DATA_DIR.resolve("gradle/languageFeature").resolve(getTestName(true).substringBefore('_'))
    }

    override fun setUpFixtures() {
        myTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName()).fixture
        codeInsightTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(myTestFixture)
        codeInsightTestFixture.setUp()
    }

    override fun tearDownFixtures() {
        runAll(
            ThrowableRunnable { codeInsightTestFixture.tearDown() },
            ThrowableRunnable {
                @Suppress("UNCHECKED_CAST")
                (this::codeInsightTestFixture as KMutableProperty0<CodeInsightTestFixture?>).set(null)
            },
            ThrowableRunnable { resetTestFixture() }
        )
    }

    // Duplicated setting of languageVersion in the after file is expected and should be fixed in KTIJ-37923
    @Test
    @TargetVersions("7.6.3 <=> 8.14.4")
    fun testUpdateExistingLanguageVersion() {
        doTest("Increase language version to 2.2")
    }

    // Duplicated setting of languageVersion in the after file is expected and should be fixed in KTIJ-37923
    @Test
    @TargetVersions("7.6.3 <=> 8.14.4")
    fun testUpdateExistingLanguageVersionKts() {
        doTest("Increase language version to 2.2")
    }

    @Test
    @TargetVersions("7.0 <=> 9.0")
    fun testUpdateExistingLanguageVersionKMP() {
        doKMPTest("Set module language version to 1.9")
    }

    @Test
    @TargetVersions("7.0 <=> 9.0")
    fun testUpdateExistingLanguageVersionKMPKts() {
        doKMPTest("Set module language version to 1.9")
    }

    private fun doKMPTest(intentionName: String) = doTest(intentionName, "src/jvmMain/kotlin/src.kt")

    private fun doTest(intentionName: String, srcFilePath: String = "src/main/kotlin/src.kt") {
        val gradleKtsFile = File(getTestDataDirectory(), "build.gradle.kts")
        val buildGradleVFile = if (gradleKtsFile.exists()) {
            createProjectSubFile("build.gradle.kts", gradleKtsFile.readText())
        } else {
            createProjectSubFile("build.gradle", File(getTestDataDirectory(), "build.gradle").readText())
        }
        val sourceVFile = createProjectSubFile(srcFilePath, File(getTestDataDirectory(), "src.kt").readText())
        importProject()
        runInEdtAndWait {
            codeInsightTestFixture.configureFromExistingVirtualFile(sourceVFile)
            codeInsightTestFixture.launchAction(codeInsightTestFixture.findSingleIntention(intentionName))
            FileDocumentManager.getInstance().saveAllDocuments()
            checkResult(buildGradleVFile)
        }
    }

    private fun checkResult(file: VirtualFile) {
        val expectedPath = File(getTestDataDirectory(), file.name + ".after")
        val expectedContent = StringUtil.convertLineSeparators(expectedPath.readText())
        val actualContent = StringUtil.convertLineSeparators(LoadTextUtil.loadText(file).toString())
        if (actualContent != expectedContent) {
            throw FileComparisonFailedError("${file.name} doesn't match", expectedContent, actualContent, expectedPath.path)
        }
    }
}