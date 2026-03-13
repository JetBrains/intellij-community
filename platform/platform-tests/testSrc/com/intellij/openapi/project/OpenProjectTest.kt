// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.ide.CommandLineProcessor
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.ProjectUtil.FolderOpeningMode.AS_FOLDER
import com.intellij.ide.impl.ProjectUtil.FolderOpeningMode.AS_PROJECT
import com.intellij.ide.impl.SelectProjectOpenProcessorDialog
import com.intellij.openapi.project.TestOpenMode.ModeFileOrFolderDefault
import com.intellij.openapi.project.TestOpenMode.ModeFolderAsFolder
import com.intellij.openapi.project.TestOpenMode.ModeFolderAsProject
import com.intellij.openapi.project.TestProjectSource.SourceCLI
import com.intellij.openapi.project.TestProjectSource.SourceOpenFileAction
import com.intellij.platform.ModuleAttachProcessor
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.projectImport.ProjectAttachProcessor
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.checkDefaultProjectAsTemplate
import com.intellij.testFramework.useProject
import com.intellij.util.io.createDirectories
import com.intellij.util.io.createParentDirectories
import com.intellij.workspaceModel.ide.ProjectRootEntity
import com.intellij.workspaceModel.ide.toPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.file.Path
import kotlin.io.path.writeText

// terms:
// valid: .idea exists
// clean: .idea doesn't exists
// existing: project directory exists
// nested: .idea exists and ../.idea exists too
// multibuild: .idea does not exist, and there are 2 marker build files (pom.xml and build.gradle)
// regular file: regular file that is not a folder

// with ability to attach - there is some defined ProjectAttachProcessor extension (e.g. WS, PS).
// with inability to attach - there is no any defined ProjectAttachProcessor extension (e.g. IU, IC).


enum class TestProjectSource { SourceOpenFileAction, SourceCLI }
enum class TestOpenMode { ModeFileOrFolderDefault, ModeFolderAsProject, ModeFolderAsFolder }

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
        Opener(SourceOpenFileAction, ModeFolderAsProject, expectedModules = listOf($$"$ROOT$"), expectedRoots = listOf($$"$ROOT$")) {
          runBlocking { ProjectUtil.openExistingDir(it, AS_PROJECT, null) }
        },

        // I don't have strong opinion about defaultProjectTemplateShouldBeAppliedOverride.
        // Weak opinion: a folder is not a project => we don't need default project settings.
        // Feel free to change the test if you have strong opinion about desired behavior.
        Opener(SourceOpenFileAction, ModeFolderAsFolder, expectedModules = emptyList(), expectedRoots = listOf($$"$ROOT$"), defaultProjectTemplateShouldBeAppliedOverride = false) {
          runBlocking { ProjectUtil.openExistingDir(it, AS_FOLDER, null) }
        },

        Opener(SourceCLI, ModeFolderAsProject, expectedModules = listOf($$"$ROOT$"), expectedRoots = listOf($$"$ROOT$")) {
          runBlocking { CommandLineProcessor.doOpenFileOrProject(it, createOrOpenExistingProject = true, false) }.project!!
        },

        // I don't have strong opinion about defaultProjectTemplateShouldBeAppliedOverride.
        // Weak opinion: a folder is not a project => we don't need default project settings.
        // Feel free to change the test if you have strong opinion about desired behavior.
        Opener(SourceCLI, ModeFileOrFolderDefault, expectedModules = emptyList(), expectedRoots = listOf($$"$ROOT$"), defaultProjectTemplateShouldBeAppliedOverride = false) {
          runBlocking { CommandLineProcessor.doOpenFileOrProject(it, createOrOpenExistingProject = false, false) }.project!!
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
    openWithOpenerAndAssertProjectState(projectDir, opener.defaultProjectTemplateShouldBeAppliedOverride ?: false)
  }

  @Test
  fun `open clean existing project dir with ability to attach`() = runBlocking(Dispatchers.Default) {
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(ModuleAttachProcessor()), disposableRule.disposable)
    val projectDir = tempDir.newPath("project")
    projectDir.createDirectories()
    openWithOpenerAndAssertProjectState(projectDir, opener.defaultProjectTemplateShouldBeAppliedOverride ?: true)
  }

  @Test
  fun `open nested existing project dir with ability to attach`() = runBlocking(Dispatchers.Default) {
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(ModuleAttachProcessor()), disposableRule.disposable)
    val projectDir = tempDir.newPath("project")
    val subProjectDir = projectDir.resolve("subproject")
    subProjectDir.resolve(".idea").createDirectories()
    projectDir.resolve(".idea").createDirectories()
    openWithOpenerAndAssertProjectState(subProjectDir, opener.defaultProjectTemplateShouldBeAppliedOverride ?: false)
  }

  @Test
  fun `open valid existing project dir with inability to attach`() = runBlocking(Dispatchers.Default) {
    // Regardless of product (Idea vs PhpStorm), if .idea directory exists, but no modules, we must run configurators to add some module.
    // Maybe not fully clear why it is performed as part of project opening and silently, but it is existing behaviour.
    // So, existing behaviour should be preserved and any changes should be done not as part of task "use unified API to open project", but separately later.
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(), disposableRule.disposable)
    val projectDir = tempDir.newPath("project")
    projectDir.resolve(".idea").createDirectories()
    openWithOpenerAndAssertProjectState(projectDir, opener.defaultProjectTemplateShouldBeAppliedOverride ?: false)
  }

  @Test
  fun `open clean existing project dir with inability to attach`() = runBlocking(Dispatchers.Default) {
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(), disposableRule.disposable)
    val projectDir = tempDir.newPath("project")
    projectDir.createDirectories()
    openWithOpenerAndAssertProjectState(projectDir, opener.defaultProjectTemplateShouldBeAppliedOverride ?: true)
  }

  @Test
  fun `open nested existing project dir with inability to attach`() = runBlocking(Dispatchers.Default) {
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(), disposableRule.disposable)
    val projectDir = tempDir.newPath("project")
    val subProjectDir = projectDir.resolve("subproject")
    subProjectDir.resolve(".idea").createDirectories()
    projectDir.resolve(".idea").createDirectories()
    openWithOpenerAndAssertProjectState(subProjectDir, opener.defaultProjectTemplateShouldBeAppliedOverride ?: false)
  }

  @Test
  fun `open multibuild existing project dir with inability to attach`() = runBlocking(Dispatchers.Default) {
    Assume.assumeTrue(
      "This test does not handle ModeFolderAsProject mode yet, because `null` from SelectProjectOpenProcessorDialog" +
      " has different behavior when opening folder from CLI and from open action, and we don't want to cement this behavior in tests.",
      opener.mode != ModeFolderAsProject,
    )

    val processorNames = ProjectOpenProcessor.EXTENSION_POINT_NAME.extensionList.map(ProjectOpenProcessor::name)
    assertThat(processorNames).`as` { "Use intellij.idea.community.main as a classpath" }.containsAll(listOf("Maven", "Gradle"))
    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(), disposableRule.disposable)
    val projectDir = setupMultibuildProject()
    var suggestedProcessors: List<String>? = null
    SelectProjectOpenProcessorDialog.setTestDialog(disposableRule.disposable) { processor, virtualFile ->
      suggestedProcessors = processor.map(ProjectOpenProcessor::name)
      null // do not open project (~cancel)
    }
    openWithOpenerAndAssertProjectState(projectDir, opener.defaultProjectTemplateShouldBeAppliedOverride ?: false) {
      assertThat(suggestedProcessors).`as`("SelectProjectOpenProcessorDialog should not be shown").isNull()
    }
  }

  @Test
  fun `open project then open regular file with inability to attach`() = runBlocking(Dispatchers.Default) {
    Assume.assumeTrue(
      "Ignore ModeFolderAsProject/ModeFolderAsFolder, because we are checking open of regular files here, not folders",
      opener.mode != ModeFolderAsProject && opener.mode != ModeFolderAsFolder,
    )

    val projectDir = tempDir.newPath("project/project")
    projectDir.resolve(".idea").createDirectories()
    val javaFileNextToDotIdea = projectDir.resolve("MyClassInProjectDir.java")
    javaFileNextToDotIdea.writeText("public class MyClassInProjectDir {}")
    val javaFileInSubdirectory = projectDir.resolve("subdir/MyClassInSubdir.java")
    javaFileInSubdirectory.createParentDirectories()
    javaFileInSubdirectory.writeText("public class MyClassInSubdir {}")

    val javaFileAboveProjectDirectory = projectDir.parent.resolve("MyClassAboveProjectDirectory.java")
    javaFileAboveProjectDirectory.writeText("public class MyClassAboveProjectDirectory {}")

    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(), disposableRule.disposable)
    opener.opener(projectDir)!!.useProject { openedProject ->
      var project = opener.opener(javaFileNextToDotIdea)
      // the file should be opened in the already opened project
      assertThat(project).isSameAs(openedProject)

      project = opener.opener(javaFileInSubdirectory)
      assertThat(project).isSameAs(openedProject)

      project = opener.opener(javaFileAboveProjectDirectory)
      assertThat(project).isSameAs(openedProject)
    }
    Unit
  }

  @Test
  fun `open project then open the the same valid existing project dir with inability to attach`() = runBlocking(Dispatchers.Default) {
    val projectDir = tempDir.newPath("project")
    projectDir.resolve(".idea").createDirectories()

    ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(), disposableRule.disposable)
    opener.opener(projectDir)!!.useProject { openedProject ->
      val project = opener.opener(projectDir)
      // this should bring already opened project to foreground
      assertThat(project).isSameAs(openedProject)
    }
    Unit
  }

  private fun setupMultibuildProject(): Path {
    val projectDir = tempDir.newPath("project")
    projectDir.createDirectories()
    projectDir.resolve("pom.xml").writeText("""
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          <modelVersion>4.0.0</modelVersion>
          <groupId>link.sharpe</groupId>
          <artifactId>mavenproject1</artifactId>
          <version>1.0-SNAPSHOT</version>
      </project>
    """.trimIndent())

    projectDir.resolve("settings.gradle").writeText("""
      rootProject.name = 'spring-petclinic'
    """.trimIndent())

    projectDir.resolve("build.gradle").writeText("""
      group = 'com.example'
      version = '1.0.0-SNAPSHOT'
    """.trimIndent())

    return projectDir
  }

  private suspend fun openWithOpenerAndAssertProjectState(
    projectDir: Path,
    defaultProjectTemplateShouldBeApplied: Boolean,
    beforeOtherChecks: ((Project) -> Unit)? = null,
  ) {
    checkDefaultProjectAsTemplate { checkDefaultProjectAsTemplateTask ->
      val project = opener.opener(projectDir)!!
      project.useProject {
        beforeOtherChecks?.invoke(project)
        assertThatProjectContainsModules(project, opener.getExpectedModules(projectDir))
        assertThatProjectContainsRootEntities(project, opener.getExpectedRoots(projectDir))
        checkDefaultProjectAsTemplateTask(project, defaultProjectTemplateShouldBeApplied)
      }
    }
  }
}

internal class Opener(
  val source: TestProjectSource,
  val mode: TestOpenMode,
  val expectedModules: List<String>,
  val expectedRoots: List<String>,
  val defaultProjectTemplateShouldBeAppliedOverride: Boolean? = null,
  val opener: (Path) -> Project?,
) {
  override fun toString() = "${source.toString().substringAfter("Source")}-${mode.toString().substringAfter("Mode")}"

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
