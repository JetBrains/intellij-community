// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl

import com.intellij.navigation.areOriginsEqual
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JBNavigateCommandTest {
  companion object {
    @JvmField @ClassRule val appRule = ApplicationRule()
  }

  @JvmField @Rule val tempDir = TemporaryDirectory()
  @JvmField @Rule val testName = TestName()
  @JvmField @Rule val projectTrackingRule = ProjectTrackingRule()
  @JvmField @Rule internal val busConnection = RecentProjectManagerListenerRule()

  fun getTestDataPath(): String = "${PlatformTestUtil.getPlatformTestDataPath()}/commands/navigate/"

  @Test fun pathWithLineColumn() = runBlocking {
    createOrLoadProject(tempDir, useDefaultProjectSettings = false) { project ->
      withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
        configure(project)
        navigate(project.name, mapOf("path" to "A.java:1:5"))
        assertThat(getCurrentElement(project).containingFile.name).isEqualTo("A.java")
        val currentLogicalPosition = getCurrentLogicalPosition(project)
        assertThat(currentLogicalPosition.line).isEqualTo(1)
        assertThat(currentLogicalPosition.column).isEqualTo(5)
      }
    }
  }

  @Test fun pathWithLine() = runBlocking {
    createOrLoadProject(tempDir, useDefaultProjectSettings = false) { project ->
      withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
        configure(project)
        navigate(project.name, mapOf("path" to "A.java:2"))
        assertThat(getCurrentElement(project).containingFile.name).isEqualTo("A.java")
        val currentLogicalPosition = getCurrentLogicalPosition(project)
        assertThat(currentLogicalPosition.line).isEqualTo(2)
      }
    }
  }

  @Test fun path1() = runBlocking {
    createOrLoadProject(tempDir, useDefaultProjectSettings = false) { project ->
      runInEdtAndWait {
        configure(project)
        navigate(project.name, mapOf("path" to "A.java"))
        assertThat(getCurrentElement(project).name).isEqualTo("A.java")
      }
    }
  }

  @Test fun compareOrigins() {
    val equalOrigins = listOf(
      "https://github.com/JetBrains/intellij.git",
      "https://github.com/JetBrains/intellij",
      "http://github.com/JetBrains/intellij",
      "ssh://git@github.com:JetBrains/intellij.git",
      "ssh://user@github.com:JetBrains/intellij.git",
      "git@github.com:JetBrains/intellij.git",
      "user@github.com:JetBrains/intellij.git",
    )

    equalOrigins.forEach { first ->
      equalOrigins.forEach { second ->
        assertTrue(areOriginsEqual(first, second), "Non equal: '$first' and '$second'")
      }
    }

    val nonEqualOrigins = listOf(
      "https://github.bom/JetBrains/intellij.git",
      "https://github.com/JetBrains/intellij.git.git",
      "http://github.com/JetBraind/intellij",
      "http://github.com:8080/JetBrains/intellij",
      "http://github.com",
      "ssh://git@github.com:JetBrains",
      "ssh://user@github.bom:JetBrains/intellij.git",
      "git@github.com:JetBrains/go",
    )
    equalOrigins.forEach { first ->
      nonEqualOrigins.forEach { second ->
        assertFalse(areOriginsEqual(first, second), "Equal: '$first' and '$second'")
      }
    }
  }

  @Test fun pathOpenProject(): Unit = runBlocking {
    var projectName: String? = null
    createOrLoadProject(tempDir, useDefaultProjectSettings = false) { project ->
      withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
        configure(project)
        projectName = project.name
      }
    }

    withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
      navigate(projectName!!, mapOf("path" to "A.java"))

      ProjectManager.getInstance().openProjects.find { it.name == projectName }!!.use { recentProject ->
        assertThat(getCurrentElement(recentProject).name).isEqualTo("A.java")
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

  private fun navigate(projectName: String, parameters: Map<String, String>) {
    val query = parameters.asSequence().fold("project=${projectName}") { acc, e -> "${acc}&${e.key}=${e.value}" }
    JBProtocolCommand.execute("idea/navigate/reference?${query}")
    UIUtil.dispatchAllInvocationEvents()
  }

  private fun getCurrentLogicalPosition(project: Project): LogicalPosition =
    FileEditorManager.getInstance(project).allEditors.map {
      (it as TextEditor).editor.offsetToLogicalPosition(it.editor.caretModel.offset)
    }.first()

  private fun getCurrentElement(project: Project): NavigatablePsiElement =
    FileEditorManager.getInstance(project).allEditors.map {
      val offset = (it as TextEditor).editor.caretModel.offset
      val psiFile = PsiManager.getInstance(project).findFile(it.file!!)!!
      PsiTreeUtil.findElementOfClassAtOffset(psiFile, offset, NavigatablePsiElement::class.java, false)!!
    }.first()
}
