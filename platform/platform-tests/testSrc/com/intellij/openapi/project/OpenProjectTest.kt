// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.ide.CommandLineProcessor
import com.intellij.ide.impl.ProjectUtil
import com.intellij.platform.ModuleAttachProcessor
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.checkDefaultProjectAsTemplate
import com.intellij.util.io.createDirectories
import com.intellij.workspaceModel.ide.ProjectRootEntity
import com.intellij.workspaceModel.ide.toPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Path

// terms:
// valid: .idea exists
// clean: .idea doesn't exists
// existing: project directory exists
// nested: .idea exists and ../.idea exists too

// with ability to attach - there is some defined ProjectAttachProcessor extension (e.g. WS, PS).
// with inability to attach - there is no any defined ProjectAttachProcessor extension (e.g. IU, IC).

@RunWith(Parameterized::class)
internal class OpenProjectTest(private val opener: Opener) {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()

    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun params(): Iterable<Opener> {
      return listOf(
        Opener("OpenFileAction-FolderAsProject", expectedModules = listOf($$"$ROOT$"), expectedRoots = listOf($$"$ROOT$")) {
          runBlocking { ProjectUtil.openExistingDir(it, null) }
        },
        Opener("CLI-FolderAsProject", expectedModules = listOf($$"$ROOT$"), expectedRoots = listOf($$"$ROOT$")) {
          runBlocking { CommandLineProcessor.doOpenFileOrProject(it, createOrOpenExistingProject = true, false) }.project!!
        },
      )
    }
  }

  @JvmField
  @Rule
  val tempDir = TemporaryDirectory()

  @JvmField
  @Rule
  val disposableRule = DisposableRule()

  @Test
  fun `open valid existing project dir with ability to attach`() = runBlocking(Dispatchers.Default) {
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(ModuleAttachProcessor()), disposableRule.disposable)
    val projectDir = tempDir.newPath("project")
    projectDir.resolve(".idea").createDirectories()
    openWithOpenerAndAssertProjectState(projectDir, defaultProjectTemplateShouldBeApplied = false)
  }

  @Test
  fun `open clean existing project dir with ability to attach`() = runBlocking(Dispatchers.Default) {
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(ModuleAttachProcessor()), disposableRule.disposable)
    val projectDir = tempDir.newPath("project")
    projectDir.createDirectories()
    openWithOpenerAndAssertProjectState(projectDir, defaultProjectTemplateShouldBeApplied = true)
  }

  @Test
  fun `open nested existing project dir with ability to attach`() = runBlocking(Dispatchers.Default) {
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(ModuleAttachProcessor()), disposableRule.disposable)
    val projectDir = tempDir.newPath("project")
    val subProjectDir = projectDir.resolve("subproject")
    subProjectDir.resolve(".idea").createDirectories()
    projectDir.resolve(".idea").createDirectories()
    openWithOpenerAndAssertProjectState(subProjectDir, defaultProjectTemplateShouldBeApplied = false)
  }

  @Test
  fun `open valid existing project dir with inability to attach`() = runBlocking(Dispatchers.Default) {
    // Regardless of product (Idea vs PhpStorm), if .idea directory exists, but no modules, we must run configurators to add some module.
    // Maybe not fully clear why it is performed as part of project opening and silently, but it is existing behaviour.
    // So, existing behaviour should be preserved and any changes should be done not as part of task "use unified API to open project", but separately later.
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(), disposableRule.disposable)
    val projectDir = tempDir.newPath("project")
    projectDir.resolve(".idea").createDirectories()
    openWithOpenerAndAssertProjectState(projectDir, defaultProjectTemplateShouldBeApplied = false)
  }

  @Test
  fun `open clean existing project dir with inability to attach`() = runBlocking(Dispatchers.Default) {
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(), disposableRule.disposable)
    val projectDir = tempDir.newPath("project")
    projectDir.createDirectories()
    openWithOpenerAndAssertProjectState(projectDir, defaultProjectTemplateShouldBeApplied = true)
  }

  @Test
  fun `open nested existing project dir with inability to attach`() = runBlocking(Dispatchers.Default) {
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(), disposableRule.disposable)
    val projectDir = tempDir.newPath("project")
    val subProjectDir = projectDir.resolve("subproject")
    subProjectDir.resolve(".idea").createDirectories()
    projectDir.resolve(".idea").createDirectories()
    openWithOpenerAndAssertProjectState(subProjectDir, defaultProjectTemplateShouldBeApplied = false)
  }

  private suspend fun openWithOpenerAndAssertProjectState(
    projectDir: Path,
    defaultProjectTemplateShouldBeApplied: Boolean,
  ) {
    checkDefaultProjectAsTemplate { checkDefaultProjectAsTemplateTask ->
      val project = opener.opener(projectDir)!!
      project.useProject {
        assertThatProjectContainsModules(project, opener.getExpectedModules(projectDir))
        assertThatProjectContainsRootEntities(project, opener.getExpectedRoots(projectDir))
        checkDefaultProjectAsTemplateTask(project, defaultProjectTemplateShouldBeApplied)
      }
    }
  }
}

internal class Opener(
  private val name: String,
  val expectedModules: List<String>,
  val expectedRoots: List<String>,
  val opener: (Path) -> Project?,
) {
  override fun toString() = name

  private fun expandPath(list: List<String>, root: Path): List<Path> {
    return list
      .map { it.replace($$"$ROOT$", root.toString()) }
      .map(Path::of)
  }

  fun getExpectedModules(projectDir: Path): List<Path> {
    return expandPath(expectedModules, projectDir)

  }

  fun getExpectedRoots(projectDir: Path): List<Path> {
    return expandPath(expectedRoots, projectDir)
  }
}

private fun assertThatProjectContainsModules(project: Project, expectedModulePaths: List<Path>) {
  val wsm = project.workspaceModel
  val modules = wsm.currentSnapshot.entities(ModuleEntity::class.java).toList()

  assertThat(modules).hasSize(expectedModulePaths.size) // at the moment we expect each module has exactly one root

  val projectModulePaths = modules
    .flatMap { it.contentRoots }
    .map { it.url.toPath() }

  assertThat(projectModulePaths)
    .`as`("Modules do not match expectations")
    .hasSameElementsAs(expectedModulePaths)
}

private fun assertThatProjectContainsRootEntities(project: Project, expectedRootPaths: List<Path>) {
  val wsm = project.workspaceModel
  val roots = wsm.currentSnapshot.entities(ProjectRootEntity::class.java).toList()

  val projectRootPaths = roots
    .map { it.root }
    .map { it.toPath() }

  assertThat(projectRootPaths)
    .`as`("ProjectRootEntities do not match expectations")
    .hasSameElementsAs(expectedRootPaths)
}
