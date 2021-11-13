// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.codeInsight.daemon.quickFix.ActionHint
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptorBase
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
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
import org.jetbrains.kotlin.idea.groovy.inspections.GradleKotlinxCoroutinesDeprecationInspection
import org.jetbrains.kotlin.idea.inspections.runInspection
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.reflect.KMutableProperty0
import kotlin.streams.asSequence

class GradleQuickFixTest : KotlinGradleImportingTestCase() {
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
    @Ignore // Import failed: A problem occurred evaluating root project 'project'
    fun testUpdateKotlinxCoroutines() {
        doGradleQuickFixTest(GradleKotlinxCoroutinesDeprecationInspection())
    }

    @Test
    @TargetVersions("6.0.1")
    fun testCreateActualForJs() = doMultiFileQuickFixTest()

    @Test
    @TargetVersions("6.0.1")
    fun testCreateActualForJsTest() = doMultiFileQuickFixTest()

    @Test
    @TargetVersions("6.0.1")
    fun testCreateActualForJvm() = doMultiFileQuickFixTest()

    @Test
    @TargetVersions("6.0.1")
    fun testCreateActualForJvmTest() = doMultiFileQuickFixTest()

    @Test
    @TargetVersions("6.0.1")
    fun testCreateActualForJvmTestWithCustomPath() = doMultiFileQuickFixTest()

    @Test
    @TargetVersions("6.0.1")
    fun testCreateActualForJvmTestWithCustomExistentPath() = doMultiFileQuickFixTest()

    @Test
    @TargetVersions("6.0.1")
    fun testCreateActualForJvmTestWithCustomPath2() = doMultiFileQuickFixTest()

    private fun doMultiFileQuickFixTest() {
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
                ) {
                    if (it.parent == projectVFile)
                        when (it.name) {
                            ".gradle", "gradle", "build" -> false
                            else -> true
                        }
                    else
                        true
                }
            }

            DirectiveBasedActionUtils.checkAvailableActionsAreExpected(ktFile, action?.let { actions - it } ?: actions)
        }
    }

    private fun doGradleQuickFixTest(localInspectionTool: LocalInspectionTool) {
        val buildGradleVFile = configureByFiles().first { it.name == "build.gradle" }
        importProject()

        applyInspectionFixes(localInspectionTool, buildGradleVFile)

        runInEdtAndWait {
            FileDocumentManager.getInstance().saveAllDocuments()
        }

        checkResult(buildGradleVFile)
    }

    private fun applyInspectionFixes(tool: LocalInspectionTool, file: VirtualFile) {
        runInEdtAndWait {
            runTestRunnable {
                val presentation = runInspection(tool, myProject, listOf(file))

                WriteCommandAction.runWriteCommandAction(myProject) {
                    val foundProblems = presentation.problemElements.values.mapNotNull { it as? ProblemDescriptorBase }
                    for (problem in foundProblems) {
                        val fixes = problem.fixes
                        if (fixes != null) {
                            fixes[0].applyFix(myProject, problem)
                        }
                    }
                }
            }
        }
    }

    private fun checkResult(file: VirtualFile) {
        val expectedFile = File(testDataDirectory(), "${file.name}.after")
        val actualText = configureKotlinVersionAndProperties(LoadTextUtil.loadText(file).toString())
        KotlinTestUtils.assertEqualsToFile(expectedFile, actualText) { s -> configureKotlinVersionAndProperties(s) }
    }
}
