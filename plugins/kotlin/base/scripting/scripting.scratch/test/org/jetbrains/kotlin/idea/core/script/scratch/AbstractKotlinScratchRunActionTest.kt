// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.scratch

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlinx.coroutines.job
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.core.script.scratch.actions.RunKotlinScratchAction
import org.jetbrains.kotlin.idea.core.script.scratch.output.ScratchToolWindowFactory
import org.jetbrains.kotlin.idea.core.script.scratch.ui.KotlinScratchFileEditorWithPreview
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.assertEqualsToFile
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.getTestDataFileName
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.TestMetadataUtil
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo

abstract class AbstractKotlinScratchRunActionTest {
    private val myFixture by codeInsightModuleFixture(projectFixture, descriptor.moduleTypeId)

    private val manager by projectFixture.fileEditorManagerFixture()

    private val project: Project get() = projectFixture.get()

    @TestDisposable
    lateinit var disposable: Disposable

    private lateinit var testMethodName: String

    private val testDataPath: String get() = TestMetadataUtil.getTestDataPath(this::class.java)

    protected abstract val isExplainEnabled: Boolean

    @BeforeEach
    fun setUp(testInfo: TestInfo) {
        testMethodName = testInfo.testMethod.get().name
        val module = myFixture.module
        val sdk = descriptor.sdk!!
        WriteAction.runAndWait<RuntimeException> { ProjectJdkTable.getInstance().addJdk(sdk, disposable) }
        ModuleRootModificationUtil.setModuleSdk(module, sdk)
        ModuleRootModificationUtil.updateModel(module) { descriptor.configureModule(module, it, it.contentEntries.single()) }
        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    fun doScratchTest() {
        val fileName = getTestDataFileName(this::class.java, testMethodName) ?: error("scratch file not found")
        val editorWithPreview = configureScratchEditor(fileName)

        val (actualOutput, previewText) = runInEdtAndGet {
            launchAction(RunKotlinScratchAction())

            PlatformTestUtil.waitWhileBusy {
                editorWithPreview.kotlinScratchFile.executor.scope.coroutineContext.job.children.any { it.isActive }
            }

            UIUtil.dispatchAllInvocationEvents()

            val consoleView = ToolWindowManager.getInstance(project).getToolWindow(ScratchToolWindowFactory.ID)?.contentManager?.contents
                ?.firstNotNullOfOrNull { it.component as? ConsoleViewImpl } ?: error("failed to get console view")
            consoleView.flushDeferredText()

            val output = consoleView.editor?.document?.text ?: error("failed to get output text")
            output to if (isExplainEnabled) editorWithPreview.dumpExplainContent() else null
        }
        val expectedOutputFile = Path(testDataPath, fileName.replace(".kts", ".output"))
        val scratchFilePath = editorWithPreview.kotlinScratchFile.virtualFile.path
        assertEqualsToFile(expectedOutputFile, actualOutput) { output ->
            output.replace(scratchFilePath, fileName)
                .replace(Regex("(Compilation failed: )(?:WARNING:[^\n]*\n|[ \t]*\n)+"), "$1")
                .replace(Regex("(?m)^WARNING:[^\n]*\n"), "")
        }

        if (previewText != null) {
            val expectedExplainFile = Path(testDataPath, fileName.replace(".kts", ".explain"))
            assertEqualsToFile(expectedExplainFile, previewText) { output ->
                output.replace(hexAddressRegex, "<address>")
            }
        }
    }

    protected fun configureScratchEditor(fileName: String): KotlinScratchFileEditorWithPreview {
        val text = Path(testDataPath, fileName).readText()

        val scratchVirtualFile = ScratchRootType.getInstance().createScratchFile(
            project,
            fileName,
            KotlinLanguage.INSTANCE,
            text,
            ScratchFileService.Option.create_if_missing
        ) ?: error("Couldn't create scratch file")

        runInEdtAndWait { myFixture.openFileInEditor(scratchVirtualFile) }

        IndexingTestUtil.waitUntilIndexesAreReady(myFixture.project)

        return runInEdtAndGet {
            val scratchFileEditor = getScratchEditorForSelectedFile(manager, myFixture.file.virtualFile)
                ?: error("Couldn't find scratch file")

            configureOptions(scratchFileEditor, text, myFixture.module, isExplainEnabled)

            scratchFileEditor
        }
    }

    protected fun launchAction(action: AnAction) {
        val e = getActionEvent(action)
        ActionUtil.updateAction(action, e)
        assertTrue(e.presentation.isEnabledAndVisible)
        ActionUtil.performAction(action, e)
    }

    private fun getActionEvent(action: AnAction): AnActionEvent {
        val context = TestDataProvider(project)
        return TestActionEvent.createTestEvent(action, context::getData)
    }

    companion object {
        private val projectFixture = projectFixture(openAfterCreation = true)
        private val descriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

        fun configureOptions(
            scratchFileEditor: KotlinScratchFileEditorWithPreview,
            fileText: String,
            module: Module?,
            isExplainEnabled: Boolean,
        ) {
            val scratchFile = scratchFileEditor.kotlinScratchFile.apply {
                saveOptions { copy(isMakeBeforeRun = false) }
            }

            if (module != null && !InTextDirectivesUtils.isDirectiveDefined(fileText, "// NO_MODULE")) {
                scratchFile.setModule(module)
            }

            if (InTextDirectivesUtils.isDirectiveDefined(fileText, "// SELECTED_JDK_FROM_PROJECT")) {
                val sdk = ProjectRootManager.getInstance(scratchFile.project).projectSdk
                    ?: module?.let { ModuleRootManager.getInstance(it).sdk }
                checkNotNull(sdk) {
                    "`// SELECTED_JDK_FROM_PROJECT` requires a project SDK or a module-attached SDK; the descriptor exposes neither"
                }
                scratchFile.saveOptions { copy(selectedJdkHome = sdk.homePath) }
            }

            scratchFileEditor.setPreviewEnabled(isExplainEnabled)
        }
    }
}

private val hexAddressRegex = Regex("@[0-9a-fA-F]+")
