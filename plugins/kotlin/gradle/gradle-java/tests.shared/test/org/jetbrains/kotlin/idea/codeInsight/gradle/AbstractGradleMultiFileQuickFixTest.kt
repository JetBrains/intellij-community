// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.gradle

import com.intellij.codeInsight.daemon.quickFix.ActionHint
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.utils.vfs.refreshAndGetVirtualDirectory
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.test.DirectiveBasedActionUtils
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.nio.file.Files
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.streams.asSequence

abstract class AbstractGradleMultiFileQuickFixTest : MultiplePluginVersionGradleImportingCodeInsightTestCase() {
    override fun testDataDirName() = "fixes"
    final override fun testDataDirectory(): File = super.testDataDirectory().resolve("before")

    open val afterDirectory get() = testDataDirectory().parentFile.resolve("after")

    protected fun doMultiFileQuickFixTest(
        ignoreChangesInBuildScriptFiles: Boolean = true,
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

                IndexingTestUtil.waitUntilIndexesAreReady(myProject)

                val expected = afterDirectory.toPath().refreshAndGetVirtualDirectory()

                val projectVFile = projectPath.refreshAndGetVirtualDirectory()

                UsefulTestCase.refreshRecursively(expected)
                UsefulTestCase.refreshRecursively(projectVFile)

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

            IndexingTestUtil.waitUntilIndexesAreReady(myProject)

            codeInsightTestFixture.doHighlighting()
            DirectiveBasedActionUtils.checkAvailableActionsAreExpected(ktFile, action?.let { actions - it } ?: actions)
            DirectiveBasedActionUtils.checkForUnexpectedErrors(ktFile)
        }
    }
}
