// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.scratch

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.core.script.scratch.actions.RunKotlinScratchAction
import org.jetbrains.kotlin.idea.core.script.scratch.ui.KotlinScratchFileEditorWithPreview
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo

@TestApplication
class KotlinScratchActionStateTest {
    companion object {
        private val projectFixture = projectFixture(openAfterCreation = true)
        private val descriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()
    }

    private val myFixture by codeInsightModuleFixture(projectFixture, descriptor.moduleTypeId)

    private val manager by projectFixture.fileEditorManagerFixture()

    private val project: Project get() = projectFixture.get()

    @TestDisposable
    lateinit var disposable: Disposable

    private lateinit var testName: String

    @BeforeEach
    fun setUp(testInfo: TestInfo) {
        testName = testInfo.testMethod.get().name.removePrefix("test")
        val module = myFixture.module
        val sdk = descriptor.sdk!!
        WriteAction.runAndWait<RuntimeException> { ProjectJdkTable.getInstance().addJdk(sdk, disposable) }
        ModuleRootModificationUtil.setModuleSdk(module, sdk)
        ModuleRootModificationUtil.updateModel(module) { descriptor.configureModule(module, it, it.contentEntries.single()) }
        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    @Test
    fun testRunActionDisabledInInteractiveMode() {
        val editor = openScratchEditor()
        val presentation = runInEdtAndGet {
            val scratchFile = editor.kotlinScratchFile
            scratchFile.setModule(myFixture.module) // baseline: with a module bound the action would be enabled
            scratchFile.saveOptions { copy(isMakeBeforeRun = false, isInteractiveMode = true) }

            updateAndGetPresentation(RunKotlinScratchAction())
        }

        assertFalse(
            presentation.isEnabled,
            "Run action must be disabled while interactive mode is on",
        )
        assertTrue(
            presentation.isVisible,
            "Run action stays visible while interactive mode is on (only disabled)",
        )
    }

    @Test
    fun testMakeBeforeRunDisabledInInteractiveMode() {
        val editor = openScratchEditor()
        val presentation = runInEdtAndGet {
            val scratchFile = editor.kotlinScratchFile
            scratchFile.setModule(myFixture.module) // make-before-run is only visible while a module is bound
            scratchFile.saveOptions { copy(isMakeBeforeRun = true, isInteractiveMode = true) }

            val makeBeforeRun = editor.getViewActionsForTesting().first()
            updateAndGetPresentation(makeBeforeRun)
        }

        assertFalse(
            presentation.isEnabled,
            "MakeBeforeRun toggle must be disabled while interactive mode is on",
        )
        assertTrue(
            presentation.isVisible,
            "MakeBeforeRun toggle stays visible while a module is bound (only disabled by interactive mode)",
        )
    }

    private fun openScratchEditor(): KotlinScratchFileEditorWithPreview {
        val name = "KotlinScratchActionStateTest_${testName}.kts"
        val vFile = ScratchRootType.getInstance().createScratchFile(
            project, name, KotlinLanguage.INSTANCE, "", ScratchFileService.Option.create_if_missing,
        ) ?: error("Failed to create scratch file $name")

        runInEdtAndWait { myFixture.openFileInEditor(vFile) }
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        return runInEdtAndGet { getScratchEditorForSelectedFile(manager, vFile) }
            ?: error("Scratch editor not opened for $name")
    }

    private fun updateAndGetPresentation(action: AnAction): Presentation {
        val event: AnActionEvent = TestActionEvent.createTestEvent(action, TestDataProvider(project)::getData)
        ActionUtil.updateAction(action, event)
        return event.presentation
    }
}
