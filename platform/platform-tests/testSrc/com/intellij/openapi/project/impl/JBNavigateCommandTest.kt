// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl

import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.nio.file.Paths

class JBNavigateCommandTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @JvmField
  @Rule
  val tempDir = TemporaryDirectory()

  @JvmField
  @Rule
  val testName = TestName()

  @Rule
  @JvmField
  val busConnection = RecentProjectManagerListenerRule()

  fun getTestDataPath(): String {
    return "${PlatformTestUtil.getPlatformTestDataPath()}/commands/navigate/"
  }

  @Test
  fun pathWithLineColumn() = runBlocking {
    createOrLoadProject(tempDir, useDefaultProjectSettings = false) { project ->
      configure(project)
      navigate(project.name, mapOf("path" to "A.java:1:5"))
      assertThat(getCurrentElement(project).containingFile.name).isEqualTo("A.java")
      val currentLogicalPosition = getCurrentLogicalPosition(project)
      assertThat(currentLogicalPosition.line).isEqualTo(1)
      assertThat(currentLogicalPosition.column).isEqualTo(5)
    }
  }

  @Test
  fun pathWithLine() = runBlocking {
    createOrLoadProject(tempDir, useDefaultProjectSettings = false) { project ->
      configure(project)
      navigate(project.name, mapOf("path" to "A.java:2"))
      assertThat(getCurrentElement(project).containingFile.name).isEqualTo("A.java")
      val currentLogicalPosition = getCurrentLogicalPosition(project)
      assertThat(currentLogicalPosition.line).isEqualTo(2)
    }
  }

  @Test
  fun path1() = runBlocking {
    createOrLoadProject(tempDir, useDefaultProjectSettings = false) { project ->
      configure(project)
      navigate(project.name, mapOf("path" to "A.java"))
      assertThat(getCurrentElement(project).name).isEqualTo("A.java")
    }
  }

  @Test
  fun pathOpenProject() = runBlocking {
    var projectName: String? = null
    createOrLoadProject(tempDir, useDefaultProjectSettings = false) { project ->
      configure(project)
      projectName = project.name
    }

    withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
      var recentProject: Project? = null
      try {
        navigate(projectName!!, mapOf("path" to "A.java"))
        UIUtil.dispatchAllInvocationEvents()

        recentProject = ProjectManager.getInstance().openProjects.find { it.name == projectName }!!
        assertThat(getCurrentElement(recentProject).name).isEqualTo("A.java")
      }
      finally {
        PlatformTestUtil.forceCloseProjectWithoutSaving(recentProject ?: return@withContext)
      }
    }
  }

  private fun configure(project: Project) {
    val basePath = Paths.get(project.basePath!!)
    val moduleManager = ModuleManager.getInstance(project)
    val projectManager = ProjectRootManagerEx.getInstanceEx(project)
    runWriteAction {
      projectManager.mergeRootsChangesDuring {
        val newModule = moduleManager.newModule(basePath.resolve("navigationCommandModule.uml").systemIndependentPath, EmptyModuleType.EMPTY_MODULE)
        FileUtil.copyDir(Paths.get(getTestDataPath(), sanitizeFileName(testName.methodName)).toFile(), basePath.toFile())

        val baseDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath.systemIndependentPath)!!
        val moduleModel = ModuleRootManager.getInstance(newModule).modifiableModel
        moduleModel.addContentEntry(baseDir).addSourceFolder(baseDir, false)
        moduleModel.commit()

        VfsTestUtil.createDir(baseDir, Project.DIRECTORY_STORE_FOLDER)
      }
    }
  }

  private fun getCurrentElement(project: Project) = getCurrentElements(project).first()

  private fun getCurrentElements(project: Project): List<NavigatablePsiElement> {
    return FileEditorManager.getInstance(project).allEditors.map {
      val textEditor = it as TextEditor
      val offset = textEditor.editor.caretModel.offset
      val file = it.file
      val psiFile = PsiManager.getInstance(project).findFile(file!!)

      PsiTreeUtil.findElementOfClassAtOffset(psiFile!!, offset, NavigatablePsiElement::class.java, false)!!
    }
  }

  private fun getCurrentLogicalPosition(project: Project): LogicalPosition {
    return FileEditorManager.getInstance(project).allEditors.map {
      (it as TextEditor).editor.offsetToLogicalPosition(it.editor.caretModel.offset)
    }.first()
  }

  private fun navigate(projectName: String, parameters: Map<String, String>) {
    val navigateCommand = JBProtocolCommand.findCommand("navigate")
    val map = hashMapOf("project" to projectName)
    map.putAll(parameters)
    navigateCommand?.perform("reference", map)
  }
}
