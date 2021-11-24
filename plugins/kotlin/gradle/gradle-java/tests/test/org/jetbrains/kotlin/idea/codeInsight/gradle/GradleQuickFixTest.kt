// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.codeInsight.daemon.quickFix.ActionHint
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import com.intellij.util.io.readText
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.reflect.KMutableProperty0
import kotlin.streams.asSequence

class GradleQuickFixTest : MultiplePluginVersionGradleImportingTestCase() {
    private lateinit var codeInsightTestFixture: CodeInsightTestFixture

    override fun testDataDirName() = "fixes"

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
            ThrowableRunnable { myTestFixture = null }
        )
    }

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForJs() = doMultiFileQuickFixTest()

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForJsTest() = doMultiFileQuickFixTest()

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForJvm() = doMultiFileQuickFixTest()

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForJvmTest() = doMultiFileQuickFixTest()

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForJvmTestWithCustomPath() = doMultiFileQuickFixTest()

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForJvmTestWithCustomExistentPath() = doMultiFileQuickFixTest()

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForNativeIOS() = doMultiFileQuickFixTest()

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForNativeIOSWithExistentPath() = doMultiFileQuickFixTest()

    @Test
    @PluginTargetVersions(pluginVersion = "1.5.31+")
    fun testCreateActualForGranularSourceSetTarget() = doMultiFileQuickFixTest()

    private fun doMultiFileQuickFixTest(ignoreChangesInBuildScriptFiles: Boolean = true) {
        configureByFiles(subPath = "before")
        val projectPath = myProjectRoot.toNioPath()

        val (mainFilePath, mainFileText) = Files.walk(projectPath).asSequence()
            .filter { it.isRegularFile() }
            .firstNotNullOfOrNull {
                val text = kotlin.runCatching { it.readText() }.getOrNull()
                if (text?.startsWith("// \"") == true) it to text else null
            } ?: error("file with action is not found")

        importProject()

        val ktFile = runReadAction {
            LocalFileSystem.getInstance().findFileByNioFile(mainFilePath)?.toPsiFile(myProject)
        } as KtFile

        val actionHint = ActionHint.parse(ktFile, mainFileText)
        codeInsightTestFixture.configureFromExistingVirtualFile(ktFile.virtualFile)

        runInEdtAndWait {
            val actions = codeInsightTestFixture.availableIntentions
            val action = actionHint.findAndCheck(actions) { "Test file: ${projectPath.relativize(mainFilePath).pathString}" }
            if (action != null) {
                action.invoke(myProject, null, ktFile)
                val expected = LocalFileSystem.getInstance().findFileByIoFile(testDataDirectory().resolve("after"))?.apply {
                    UsefulTestCase.refreshRecursively(this)
                } ?: error("Expected directory is not found")

                val projectVFile = (LocalFileSystem.getInstance().findFileByIoFile(File("$projectPath"))
                    ?: error("VirtualFile is not found for project path"))

                PlatformTestUtil.assertDirectoriesEqual(
                    expected,
                    projectVFile,
                    fun(vFile: VirtualFile): Boolean {
                        if (vFile.parent == projectVFile) {
                            when (vFile.name) {
                                ".gradle", "gradle", "build" -> return false
                            }
                        }

                        if (ignoreChangesInBuildScriptFiles && ".gradle" in vFile.name) return false

                        return true
                    },
                )
            }

            codeInsightTestFixture.doHighlighting()
            DirectiveBasedActionUtils.checkAvailableActionsAreExpected(ktFile, action?.let { actions - it } ?: actions)
            DirectiveBasedActionUtils.checkForUnexpectedErrors(ktFile)
        }
    }
}
