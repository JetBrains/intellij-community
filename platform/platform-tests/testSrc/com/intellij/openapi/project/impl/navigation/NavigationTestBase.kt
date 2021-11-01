// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl.navigation

import com.intellij.navigation.LocationInFile
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.RecentProjectManagerListenerRule
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.*
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.ClassRule
import org.junit.Rule
import org.junit.rules.TestName
import java.nio.file.Paths

abstract class NavigationTestBase {
  @JvmField @Rule val tempDir = TemporaryDirectory()
  @JvmField @Rule val testName = TestName()
  @JvmField @Rule val projectTrackingRule = ProjectTrackingRule()
  @JvmField @Rule internal val busConnection = RecentProjectManagerListenerRule()

  lateinit var project: Project

  open val testDataPath get() = "${PlatformTestUtil.getPlatformTestDataPath()}/commands/navigate/"

  protected fun runNavigationTest(navigationAction: () -> Unit, checkAction: () -> Unit) = runBlocking {
    createOrLoadProject(tempDir, useDefaultProjectSettings = false) { project ->
      withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
        setUpProject(project)

        navigationAction()
        UIUtil.dispatchAllInvocationEvents()
        checkAction()
      }
    }
  }

  protected open fun setUpProject(project: Project) {
    this.project = project
    val basePath = Paths.get(project.basePath!!)
    val moduleManager = ModuleManager.getInstance(project)
    val projectManager = ProjectRootManagerEx.getInstanceEx(project)
    runWriteAction {
      projectManager.mergeRootsChangesDuring {
        val newModule = moduleManager.newModule(basePath.resolve("navigationModule.iml").systemIndependentPath, EmptyModuleType.EMPTY_MODULE)
        FileUtil.copyDir(Paths.get(testDataPath, sanitizeFileName(testName.methodName)).toFile(), basePath.toFile())

        val baseDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath.systemIndependentPath)!!
        val moduleModel = ModuleRootManager.getInstance(newModule).modifiableModel
        moduleModel.addContentEntry(baseDir).addSourceFolder(baseDir, false)
        moduleModel.commit()

        VfsTestUtil.createDir(baseDir, Project.DIRECTORY_STORE_FOLDER)
      }
    }
  }

  protected val currentCharacterZeroBasedPosition: LocationInFile get() =
    FileEditorManager.getInstance(project).allEditors.asSequence().map {
      with((it as TextEditor).editor) {
        val offsetTotal = caretModel.offset
        val line = offsetToLogicalPosition(offsetTotal).line
        val offsetOfLine = logicalPositionToOffset(LogicalPosition(line, 0))
        LocationInFile(line, offsetTotal - offsetOfLine)
      }
    }.first()

  protected val currentLogicalPosition: LogicalPosition get() =
    FileEditorManager.getInstance(project).allEditors.asSequence().map {
      (it as TextEditor).editor.offsetToLogicalPosition(it.editor.caretModel.offset)
    }.first()

  protected val currentElement: NavigatablePsiElement get() =
    FileEditorManager.getInstance(project).allEditors.asSequence().map {
      val offset = (it as TextEditor).editor.caretModel.offset
      val psiFile = PsiManager.getInstance(project).findFile(it.file!!)!!
      PsiTreeUtil.findElementOfClassAtOffset(psiFile, offset, NavigatablePsiElement::class.java, false)!!
    }.first()
}