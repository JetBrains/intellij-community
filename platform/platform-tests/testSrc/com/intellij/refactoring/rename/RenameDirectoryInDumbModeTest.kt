// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.refactoring.actions.RenameElementAction
import com.intellij.refactoring.actions.RenameFileAction
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.utils.vfs.createDirectory
import com.intellij.testFramework.utils.vfs.createFile
import com.intellij.ui.UiInterceptors
import com.intellij.workspaceModel.ide.registerProjectRoot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RegistryKey("rename.files.in.dumb.mode.enable", "true")
@TestApplication
class RenameDirectoryInDumbModeTest {

  private val projectFixture = projectFixture(openAfterCreation = true)
  private val directoryFixture: TestFixture<VirtualFile> = testFixture {
    initialized(VfsUtil.findFile(tempPathFixture().init(), true)!!) {}
  }

  private val project get() = projectFixture.get()
  private val srcDir get() = directoryFixture.get()

  @Test
  fun `rename file in dumb mode`() = runBlocking {
    val file = writeAction { srcDir.createFile("testFile") }
    val psiFile = readAction { PsiManager.getInstance(project).findFile(file) }
    assertNotNull(psiFile, "PsiDirectory should not be null")
    registerProjectRoot(project, file.toNioPath())

    rename(psiFile, "renamedFile")
    assertEquals("renamedFile", psiFile.name, "File should be renamed even in dumb mode")
  }

  @Test
  fun `rename file action works in dumb mode`() = runBlocking {
    val file = writeAction { srcDir.createFile("testFile") }
    val psiFile = readAction { PsiManager.getInstance(project).findFile(file) }
    assertNotNull(psiFile, "PsiDirectory should not be null")
    registerProjectRoot(project, file.toNioPath())

    rename(psiFile, "renamedFile", action = RenameFileAction())
    assertEquals("renamedFile", psiFile.name, "File should be renamed even in dumb mode")
  }

  @Test
  fun `rename file action not rename directories in dumb mode`() = runBlocking {
    val dir = writeAction { srcDir.createDirectory("testDir") }
    val psiDir = readAction { PsiManager.getInstance(project).findDirectory(dir) }
    assertNotNull(psiDir, "PsiDirectory should not be null")
    registerProjectRoot(project, dir.toNioPath())

    val event = createEvent(project, psiDir)
    val action = RenameFileAction()
    action.update(event)
    assertFalse(event.presentation.isEnabledAndVisible, "Rename file action should be disabled on directory, even in dumb mode")
  }

  @Test
  fun `rename directory in dumb mode`() = runBlocking {
    val directory = writeAction { srcDir.createChildDirectory(this, "testDir") }
    val psiDirectory = readAction { PsiManager.getInstance(project).findDirectory(directory) }
    assertNotNull(psiDirectory, "PsiDirectory should not be null")
    registerProjectRoot(project, directory.toNioPath())

    rename(psiDirectory, "renamedDir")
    assertEquals("renamedDir", psiDirectory.name, "Directory should be renamed even in dumb mode")
  }

  private fun rename(psiElement: PsiElement, newName: String, action: AnAction = RenameElementAction()) {
    interceptRenameDialogAndInvokeRename(newName)

    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      runInEdtAndWait {
        runReadAction {
          action.actionPerformed(createEvent(project, psiElement))
        }
      }
    }
  }
}

/**
 * intercepts rename dialog and invokes rename to [newName]
 */
private fun interceptRenameDialogAndInvokeRename(newName: String) {
  UiInterceptors.register(object : UiInterceptors.UiInterceptor<RenameDialog>(RenameDialog::class.java) {
    override fun doIntercept(component: RenameDialog) {
      component.performRename(newName)
    }
  })
}

private fun createEvent(project: Project, psiElement: PsiElement): AnActionEvent {
  val context = SimpleDataContext.builder().add(CommonDataKeys.PROJECT, project).add(CommonDataKeys.PSI_ELEMENT, psiElement).build()
  return TestActionEvent.createTestEvent(context)
}
