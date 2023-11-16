// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl.navigation

import com.intellij.navigation.finder.LocationInFile
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.rules.TestName
import java.nio.file.Path
import java.nio.file.Paths

abstract class NavigationTestBase {
  @JvmField @Rule val tempDir = TemporaryDirectory()
  @JvmField @Rule val testName = TestName()
  @JvmField @Rule val projectTrackingRule = ProjectTrackingRule()
  @JvmField @Rule internal val busConnection = RecentProjectManagerListenerRule()

  lateinit var project: Project

  open val testDataPath: String
    get() = "${PlatformTestUtil.getPlatformTestDataPath()}/commands/navigate/"

  protected inline fun runNavigationTest(crossinline navigationAction: suspend () -> Unit, crossinline checkAction: () -> Unit) {
    runInProjectTest {
      navigationAction()
      withContext(Dispatchers.EDT) {
        checkAction()
      }
    }
  }

  protected inline fun runInProjectTest(crossinline block: suspend () -> Unit) {
    runBlocking {
      createOrLoadProject(tempDir, useDefaultProjectSettings = false) { project ->
        setUpProject(project)
        block()
      }
    }
  }

  protected open suspend fun setUpProject(project: Project) {
    this.project = project
    val basePath = Path.of(project.basePath!!)
    val moduleManager = ModuleManager.getInstance(project)
    val projectManager = ProjectRootManagerEx.getInstanceEx(project)
    writeAction {
      projectManager.mergeRootsChangesDuring {
        val newModule = moduleManager.newModule(
          basePath.resolve("navigationModule.iml").systemIndependentPath,
          EmptyModuleType.EMPTY_MODULE
        )
        FileUtil.copyDir(Paths.get(testDataPath, sanitizeFileName(testName.methodName)).toFile(), basePath.toFile())

        val baseDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath.systemIndependentPath)!!
        val moduleModel = ModuleRootManager.getInstance(newModule).modifiableModel
        moduleModel.addContentEntry(baseDir).addSourceFolder(baseDir, false)
        moduleModel.commit()

        VfsTestUtil.createDir(baseDir, Project.DIRECTORY_STORE_FOLDER)
      }
    }
  }

  protected fun getCurrentCharacterZeroBasedPosition(): LocationInFile {
    return FileEditorManager.getInstance(project).allEditors.asSequence().map {
      with((it as TextEditor).editor) {
        val offsetTotal = caretModel.offset
        val line = offsetToLogicalPosition(offsetTotal).line
        val offsetOfLine = logicalPositionToOffset(LogicalPosition(line, 0))
        LocationInFile(line, offsetTotal - offsetOfLine)
      }
    }.first()
  }

  protected fun getCurrentLogicalPosition(): LogicalPosition {
    return FileEditorManager.getInstance(project).allEditors.asSequence().map {
      (it as TextEditor).editor.offsetToLogicalPosition(it.editor.caretModel.offset)
    }.first()
  }

  protected fun getCurrentElement(): NavigatablePsiElement {
    return FileEditorManager.getInstance(project).allEditors.asSequence().map {
      val offset = (it as TextEditor).editor.caretModel.offset
      val psiFile = PsiManager.getInstance(project).findFile(it.file!!)!!
      PsiTreeUtil.findElementOfClassAtOffset(psiFile, offset, NavigatablePsiElement::class.java, false)!!
    }.first()
  }
}
