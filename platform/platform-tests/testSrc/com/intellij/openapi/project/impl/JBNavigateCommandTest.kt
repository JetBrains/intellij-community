// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl

import com.intellij.ide.RecentProjectsManager
import com.intellij.idea.Bombed
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.JBProtocolCommand
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase
import java.io.IOException

class JBNavigateCommandTest : PlatformTestCase() {
  fun getTestDataPath(): String {
    return "${PlatformTestUtil.getPlatformTestDataPath()}/commands/navigate/"
  }

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    //this is a service. Initialized lazily
    RecentProjectsManager.getInstance()
  }

  fun testPath1() {
    val project = configureProject()

    navigate(project.name, mapOf("path" to "A.java"))

    TestCase.assertEquals(getCurrentElement(project).name, "A.java")
    PlatformTestUtil.forceCloseProjectWithoutSaving(project)
  }

  @Bombed(year = 2019, month = 7, day = 1, user = "dima")
  fun testFqn1() {
    val project = configureProject()

    navigate(project.name, mapOf("fqn" to "A"))

    UIUtil.dispatchAllInvocationEvents()
    TestCase.assertEquals(getCurrentElement(project).name, "A")
    PlatformTestUtil.forceCloseProjectWithoutSaving(project)
  }

  @Bombed(year = 2019, month = 7, day = 1, user = "dima")
  fun testFqnMethod() {
    val project = configureProject()

    navigate(project.name, mapOf("fqn" to "A#main"))

    UIUtil.dispatchAllInvocationEvents()
    TestCase.assertEquals(getCurrentElement(project).name, "main")
    PlatformTestUtil.forceCloseProjectWithoutSaving(project)
  }

  @Bombed(year = 2019, month = 7, day = 1, user = "dima")
  fun testFqnMultipleMethods() {
    val project = configureProject()

    navigate(project.name, mapOf("fqn1" to "A1#main1",
                                 "fqn2" to "A2#main2"))

    UIUtil.dispatchAllInvocationEvents()
    val elements = getCurrentElements(project)
    TestCase.assertEquals(elements[0].name, "main1")
    TestCase.assertEquals(elements[1].name, "main2")
    PlatformTestUtil.forceCloseProjectWithoutSaving(project)
  }

  @Bombed(year = 2019, month = 7, day = 1, user = "dima")
  fun testFqnConstant() {
    val project = configureProject()

    navigate(project.name, mapOf("fqn" to "A#RUN_CONFIGURATION_AD_TEXT"))

    UIUtil.dispatchAllInvocationEvents()
    TestCase.assertEquals(getCurrentElement(project).name, "RUN_CONFIGURATION_AD_TEXT")
    PlatformTestUtil.forceCloseProjectWithoutSaving(project)
  }

  fun testPathOpenProject() {
    val projectName = "navigationCommandProject"
    val actualProjectName = actualProjectName(projectName)
    //step 1: create and open a project
    val project = createAndOpenProject(projectName)
    configure(project)
    //step 1: close project
    PlatformTestUtil.forceCloseProjectWithoutSaving(project)

    navigate(actualProjectName, mapOf("path" to "A.java"))

    UIUtil.dispatchAllInvocationEvents()
    val recentProject = ProjectManager.getInstance().openProjects.find { it.name == actualProjectName }!!
    TestCase.assertEquals(getCurrentElement(recentProject).name, "A.java")
    PlatformTestUtil.forceCloseProjectWithoutSaving(recentProject)
  }

  private fun configureProject(): Project {
    val project = createAndOpenProject()
    configure(project)
    return project
  }

  private fun configure(project: Project) {
    val basePath = project.basePath!!
    val baseDir = LocalFileSystem.getInstance().findFileByPath(basePath)!!

    WriteAction.run<RuntimeException> {
      ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring {
        WriteCommandAction.writeCommandAction(project).run<IOException> {
          val newModule = createModuleAt("navigationCommandModule", project, EmptyModuleType.getInstance(), basePath)

          val testFolder = LocalFileSystem.getInstance().findFileByPath("${getTestDataPath()}/${getTestName(true)}")!!
          copyDirContentsTo(testFolder, baseDir)

          val moduleModel = ModuleRootManager.getInstance(newModule).modifiableModel
          moduleModel.addContentEntry(baseDir).addSourceFolder(baseDir, false)

          ApplicationManager.getApplication().runWriteAction { moduleModel.commit() }

          VfsTestUtil.createDir(baseDir, Project.DIRECTORY_STORE_FOLDER)
        }
      }
    }
  }

  private fun getCurrentElement(project: Project): NavigatablePsiElement {
    return getCurrentElements(project)[0]
  }

  private fun getCurrentElements(project: Project): List<NavigatablePsiElement> {
    return FileEditorManager.getInstance(project).allEditors.map {
      val textEditor = it as TextEditor
      val offset = textEditor.editor.caretModel.offset
      val file = it.file
      val psiFile = PsiManager.getInstance(project).findFile(file!!)

      PsiTreeUtil.findElementOfClassAtOffset(psiFile!!, offset, NavigatablePsiElement::class.java, false)!!
    }
  }

  private fun navigate(projectName: String, parameters: Map<String, String>) {
    val navigateCommand = JBProtocolCommand.findCommand("navigate")
    val map = hashMapOf("project" to projectName)
    map.putAll(parameters)
    navigateCommand?.perform("reference", map)
  }

  private fun createAndOpenProject(name: String = getTestName(true)): Project {
    val path = createTempDir(name)
    val manager = ProjectManagerEx.getInstanceEx()
    var project: Project? = manager.createProject("navigateToProject", path.path)
    project!!.save()
    PlatformTestUtil.forceCloseProjectWithoutSaving(project)
    project = manager.loadAndOpenProject(path)
    return project!!
  }

  private fun actualProjectName(projectName: String) = "idea_test_$projectName"
}
