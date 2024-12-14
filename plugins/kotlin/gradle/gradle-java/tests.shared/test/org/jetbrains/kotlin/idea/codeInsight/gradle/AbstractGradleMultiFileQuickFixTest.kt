// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.codeInsight.daemon.quickFix.ActionHint
import com.intellij.openapi.application.*
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.*
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import kotlinx.coroutines.*
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurationService
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.streams.asSequence
import kotlin.time.Duration.Companion.minutes

abstract class AbstractGradleMultiFileQuickFixTest : MultiplePluginVersionGradleImportingCodeInsightTestCase(), ExpectedPluginModeProvider {
    override fun testDataDirName(): String = "fixes"
    final override fun testDataDirectory(): File = super.testDataDirectory().resolve("before")

    open val afterTestDataDirectory: File
        get() = testDataDirectory().parentFile.resolve("after")

    /*
    * Check unexpected diagnostics in the file.
    * Called from a read action.
    */
    protected abstract fun checkUnexpectedErrors(ktFile: KtFile)

    private lateinit var afterDirectory: Path

    @OptIn(ExperimentalPathApi::class)
    override fun setUp() {
        setUpWithKotlinPlugin {
            super.setUp()

            /* Setup 'after' directory: Ensure that we process it similar to the 'before', by also replacing test properties */
            afterDirectory = TemporaryDirectory.generateTemporaryPath("${testDataDirName()}.after")

            /* Some quick fix (e.g. AddKotlinLibraryQuickFix) will modify build scripts *and* re-sync the project */
            AutoImportProjectTracker.enableAutoReloadInTests(testRootDisposable)
        }
    }

    @OptIn(ExperimentalPathApi::class)
    override fun tearDown() {
        RunAll.runAll(
            { afterDirectory.deleteRecursively() },
            { super.tearDown() },
        )
    }

    protected fun doMultiFileQuickFixTest(
        ignoreChangesInBuildScriptFiles: Boolean = true,
        afterDirectorySanitizer: (sourcePath: Path, text: String) -> String = { _, text -> text },
        additionalResultFileFilter: (VirtualFile) -> Boolean = { true },
    ) {
        configureByFiles()
        val projectPath = myProjectRoot.toNioPath()

        val (mainFilePath, mainFileText) = Files.walk(projectPath).asSequence()
            .filter { it.isRegularFile() }
            .firstNotNullOfOrNull {
                val text = kotlin.runCatching { it.readText() }.getOrNull()
                if (text?.startsWith("// \"") == true) it to text else null
            } ?: error("file with action is not found")

        importProject()

        copyAfterDirectory(afterDirectorySanitizer)

        val ktFile = runReadAction {
            LocalFileSystem.getInstance().findFileByNioFile(mainFilePath)?.toPsiFile(myProject)
        } as KtFile

        val actionHint = ActionHint.parse(ktFile, mainFileText)
        codeInsightTestFixture.configureFromExistingVirtualFile(ktFile.virtualFile)

        timeoutRunBlocking(3.minutes) {
            val actions = codeInsightTestFixture.availableIntentions
            val action = actionHint.findAndCheck(actions) { "Test file: ${projectPath.relativize(mainFilePath).pathString}" }
            if (action != null) {
                codeInsightTestFixture.launchAction(action)
                KotlinProjectConfigurationService.getInstance(project).awaitSyncFinished()

                IndexingTestUtil.waitUntilIndexesAreReady(myProject)

                val expected = afterDirectory.refreshAndGetVirtualDirectory()
                val projectVFile = projectPath.refreshAndGetVirtualDirectory()

                refreshRecursively(expected)
                refreshRecursively(projectVFile)

                withContext(Dispatchers.EDT) {
                    writeIntentReadAction {
                        PlatformTestUtil.assertDirectoriesEqual(
                            expected,
                            projectVFile,
                            fun(vFile: VirtualFile): Boolean {
                                if (vFile.parent == projectVFile) {
                                    when (vFile.name) {
                                        ".gradle", "gradle", "build", "gradle.properties", "gradlew", "gradlew.bat", ".kotlin" -> return false
                                    }
                                }

                                if (ignoreChangesInBuildScriptFiles && ".gradle" in vFile.name) return false

                                return additionalResultFileFilter(vFile)
                            },
                        )
                    }
                }
            }

            IndexingTestUtil.waitUntilIndexesAreReady(myProject)

            codeInsightTestFixture.doHighlighting()

            readActionBlocking {
                DirectiveBasedActionUtils.checkAvailableActionsAreExpected(ktFile, action?.let { actions - it } ?: actions)
                checkUnexpectedErrors(ktFile)
            }
        }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun copyAfterDirectory(afterDirectorySanitizer: (Path, String) -> String) {
        afterTestDataDirectory.toPath().copyToRecursively(afterDirectory, followLinks = true, copyAction = { source, target ->

            if (source.isDirectory()) {
                target.createDirectory()
            }

            if (source.isRegularFile()) {
                target.writeText(
                    afterDirectorySanitizer(source, configureKotlinVersionAndProperties(source.readText()))
                )
            }

            CopyActionResult.CONTINUE
        })
    }
}
