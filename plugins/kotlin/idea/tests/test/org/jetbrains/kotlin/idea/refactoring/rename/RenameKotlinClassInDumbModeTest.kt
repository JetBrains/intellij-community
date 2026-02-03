// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.actions.RenameElementAction
import com.intellij.refactoring.actions.RenameFileAction
import com.intellij.refactoring.rename.RenameHandlerRegistry
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.UiInterceptors
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

class RenameKotlinClassInDumbModeTest : KotlinLightCodeInsightFixtureTestCase() {

    fun `test rename kotlin class in dumb mode renames only parent file`() {
        myFixture.configureByText("TestClass.kt", "class TestClass {}")

        val ktFile = myFixture.file as KtFile
        val ktClass = ktFile.declarations.filterIsInstance<KtClass>().first()

        interceptRenameDialogAndInvokeRename("RenamedClass.kt")

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            runInEdtAndWait {
                RenameElementAction().actionPerformed(createEvent(project, ktClass))
            }
        }

        // class name is expected to remain the same, only file name is changed
        assertEquals("TestClass", ktClass.name)
        assertEquals("RenamedClass.kt", ktFile.name)
    }

    fun `test rename file action works in dumb mode`() {
        myFixture.configureByText("TestClass.kt", "class TestClass {}")

        val ktFile = myFixture.file as KtFile
        val ktClass = ktFile.declarations.filterIsInstance<KtClass>().first()
        interceptRenameDialogAndInvokeRename("RenamedFile.kt")
        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            runInEdtAndWait {
                RenameFileAction().actionPerformed(createEvent(project, ktClass))
            }
        }

        // class name is expected to remain the same, "rename file action" only renames file
        assertEquals("TestClass", ktClass.name)
        assertEquals("RenamedFile.kt", ktFile.name)
    }

    fun `test rename kotlin class in dumb mode has no rename handlers`() {
        // can't test rename action, because test will fail because of "no dialog shown"
        // this should test essentially the same

        myFixture.configureByText("FileName.kt", "class DifferentClass {}")

        val ktFile = myFixture.file as KtFile
        val ktClass = ktFile.declarations.filterIsInstance<KtClass>().first()

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            val dataContext = createEvent(project, ktClass).dataContext
            val handlers = RenameHandlerRegistry.getInstance().getRenameHandlers(dataContext)
            val usableHandlers = handlers.filter { DumbService.getInstance(project).isUsableInCurrentContext(it) }
            assertTrue("Rename should be unavailable in dumb mode when class name differs from file", usableHandlers.isEmpty())
        }

        assertEquals("DifferentClass", ktClass.name)
        assertEquals("FileName.kt", ktFile.name)
    }

    private fun interceptRenameDialogAndInvokeRename(newName: String) {
        UiInterceptors.register(object : UiInterceptors.UiInterceptor<RenameDialog>(RenameDialog::class.java) {
            override fun doIntercept(component: RenameDialog) {
                component.performRename(newName)
            }
        })
    }

    private fun createEvent(project: Project, psiElement: PsiElement): AnActionEvent {
        val context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.PSI_ELEMENT, psiElement)
            .build()
        return TestActionEvent.createTestEvent(context)
    }
}
