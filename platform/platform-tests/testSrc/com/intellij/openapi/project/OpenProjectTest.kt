// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.ide.CommandLineProcessor
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.ProjectUtil.FolderOpeningMode.AS_FOLDER
import com.intellij.ide.impl.ProjectUtil.FolderOpeningMode.AS_PROJECT
import com.intellij.ide.impl.SelectProjectOpenProcessorDialog
import com.intellij.openapi.Disposable
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
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.TemporaryDirectoryExtension
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.rules.checkDefaultProjectAsTemplate
import com.intellij.testFramework.useProject
import com.intellij.util.io.createDirectories
import com.intellij.util.io.createParentDirectories
import com.intellij.workspaceModel.ide.ProjectRootEntity
import com.intellij.workspaceModel.ide.toPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import org.junitpioneer.jupiter.cartesian.ArgumentSets
import org.junitpioneer.jupiter.cartesian.CartesianTest
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.writeText

// terms:
// valid: .idea (.ipr) exists
// clean: .idea (.ipr) doesn't exist
// project directory: directory that contains .idea, .ipr, root maven or root gradle file
// existing: project directory exists
// nested: .idea (.ipr) exists and ../.idea (../.ipr) exists too
// multibuild: .idea (.ipr) does not exist, and there are 2 marker build files (pom.xml and build.gradle)
// regular file: regular file that is not a folder

// with ability to attach - there is some defined ProjectAttachProcessor extension (e.g. WS, PS).
// with inability to attach - there is no any defined ProjectAttachProcessor extension (e.g. IU, IC).


enum class TestProjectSource { SourceOpenFileAction, SourceCLI }
enum class TestOpenMode { ModeFileOrFolderDefault, ModeFolderAsProject, ModeFolderAsFolder }

internal class ExpectedProjectState(
  private val resolveRoot: Path,
  private val expectedModuleContentRoots: List<String>,
  private val expectedProjectRoots: List<String>,
) {
  private fun expandPath(list: List<String>, root: Path): List<Path> {
    return list
      .map { it.replace($$"$ROOT$", root.toString()) }
      .map(Path::of)
  }

  fun getExpectedModules(): List<Path> {
    return expandPath(expectedModuleContentRoots, resolveRoot)

  }

  fun getExpectedRoots(): List<Path> {
    return expandPath(expectedProjectRoots, resolveRoot)
  }
}

private val emptyProject = { resolveRoot: Path ->
  ExpectedProjectState(resolveRoot, emptyList(), listOf($$"$ROOT$"))
}

private val autodetectedSingleModuleProject = { resolveRoot: Path ->
  ExpectedProjectState(resolveRoot, listOf($$"$ROOT$"), listOf($$"$ROOT$"))
}

internal enum class AttachProcessors {
  EmptyAttachProcessors {
    override fun configureAttachProcessors(disposable: Disposable) {
      ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(), disposable)
    }
  },
  NonEmptyAttachProcessors {
    override fun configureAttachProcessors(disposable: Disposable) {
      ExtensionTestUtil.maskExtensions(ProjectAttachProcessor.EP_NAME, listOf(ModuleAttachProcessor()), disposable)
    }
  },
  ;

  abstract fun configureAttachProcessors(disposable: Disposable)
}

internal enum class IdeaProjectMaker {
  EmptyIdeaDirectory {
    override fun makeProject(projectDir: Path): Path {
      val projectPath = projectDir.resolve(".idea")
      projectPath.createDirectories()
      return projectPath
    }

    override fun getExpectedProjectState(projectDir: Path): ExpectedProjectState {
      return autodetectedSingleModuleProject(projectDir)
    }
  },
  IdeaDirectory {
    override fun makeProject(projectDir: Path): Path {
      val dotIdeaPath = projectDir.resolve(".idea")
      dotIdeaPath.createDirectories()

      dotIdeaPath.resolve("modules.xml").writeText($$"""
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="ProjectModuleManager">
            <modules>
              <module fileurl="file://$PROJECT_DIR$/dotIdeaModule01.iml" filepath="$PROJECT_DIR$/dotIdeaModule01.iml" />
            </modules>
          </component>
        </project>
      """.trimIndent())

      projectDir.resolve("dotIdeaModule01.iml").writeText($$"""
        <module relativePaths="true" type="JAVA_MODULE" version="4">
          <component name="NewModuleRootManager" >
            <content url="file://$MODULE_DIR$/dotIdea_mod1">
              <sourceFolder url="file://$MODULE_DIR$/src" isTestSource="false" />
            </content>
          </component>
        </module>
      """.trimIndent())

      return dotIdeaPath
    }

    override fun getExpectedProjectState(projectDir: Path): ExpectedProjectState {
      return ExpectedProjectState(projectDir, listOf($$"$ROOT$/dotIdea_mod1"), listOf($$"$ROOT$"))
    }
  },
  IprFile {
    override fun makeProject(projectDir: Path): Path {
      projectDir.createDirectories()
      val projectPath = projectDir.resolve("project.ipr")
      projectPath.writeText($$"""
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="ProjectRootManager" version="2" />
          <component name="ProjectModuleManager">
            <modules>
              <module fileurl="file://$PROJECT_DIR$/iprModule01.iml" filepath="$PROJECT_DIR$/iprModule01.iml" />
            </modules>
          </component>
        </project>
      """.trimIndent())

      projectDir.resolve("iprModule01.iml").writeText($$"""
        <module relativePaths="true" type="JAVA_MODULE" version="4">
          <component name="NewModuleRootManager" >
            <content url="file://$MODULE_DIR$/ipr_mod1">
              <sourceFolder url="file://$MODULE_DIR$/src" isTestSource="false" />
            </content>
          </component>
        </module>
      """.trimIndent())

      return projectPath
    }

    override fun getExpectedProjectState(projectDir: Path): ExpectedProjectState {
      return ExpectedProjectState(projectDir, listOf($$"$ROOT$/ipr_mod1"), listOf($$"$ROOT$"))
    }
  },
  ;

  abstract fun makeProject(projectDir: Path): Path

  abstract fun getExpectedProjectState(projectDir: Path): ExpectedProjectState
}

@TestApplication
internal class OpenProjectTest {
  companion object {
    @JvmStatic
    fun openers(): Iterable<Opener> {
      return listOf(
        Opener(SourceOpenFileAction, ModeFolderAsProject) {
          runBlocking { ProjectUtil.openExistingDir(it, AS_PROJECT, null) }
        },

        // I don't have strong opinion about defaultProjectTemplateShouldBeAppliedOverride.
        // Weak opinion: a folder is not a project => we don't need default project settings.
        // Feel free to change the test if you have strong opinion about desired behavior.
        Opener(SourceOpenFileAction, ModeFolderAsFolder, defaultProjectTemplateShouldBeAppliedOverride = false) {
          runBlocking { ProjectUtil.openExistingDir(it, AS_FOLDER, null) }
        },

        Opener(SourceCLI, ModeFolderAsProject) {
          runBlocking { CommandLineProcessor.doOpenFileOrProject(it, createOrOpenExistingProject = true, false) }.project!!
        },

        // I don't have strong opinion about defaultProjectTemplateShouldBeAppliedOverride.
        // Weak opinion: a folder is not a project => we don't need default project settings.
        // Feel free to change the test if you have strong opinion about desired behavior.
        Opener(SourceCLI, ModeFileOrFolderDefault, defaultProjectTemplateShouldBeAppliedOverride = false) {
          runBlocking { CommandLineProcessor.doOpenFileOrProject(it, createOrOpenExistingProject = false, false) }.project!!
        },
      )
    }

    @JvmStatic
    @Suppress("unused")
    fun opener_X_attachProcessors(): ArgumentSets =
      ArgumentSets.argumentsForFirstParameter(openers().toList())
        .argumentsForNextParameter(AttachProcessors.entries)

    @JvmStatic
    @Suppress("unused")
    fun opener_X_ideaProjectMaker_X_attachProcessors(): ArgumentSets =
      ArgumentSets.argumentsForFirstParameter(openers().toList())
        .argumentsForNextParameter(IdeaProjectMaker.entries)
        .argumentsForNextParameter(AttachProcessors.entries)
  }

  @JvmField
  @RegisterExtension
  val tempDir = TemporaryDirectoryExtension()

  @TestDisposable
  lateinit var disposable: Disposable

  private fun checkOpenerIsApplicableToTargetPath(opener: Opener, pathToOpen: Path) {
    Assumptions.assumeFalse(
      (opener.mode == ModeFolderAsFolder || opener.mode == ModeFolderAsProject) && pathToOpen.isRegularFile(),
      "$opener can only open folders. It cannot be applied to $pathToOpen which is not a directory"
    )
  }

  private fun calcExpectedProjectState(opener: Opener, maker: IdeaProjectMaker, pathToOpen: Path): (Path) -> ExpectedProjectState {
    return calcExpectedProjectState(opener, { maker.getExpectedProjectState(it) }, pathToOpen)
  }

  private fun calcExpectedProjectState(
    opener: Opener,
    makerSuggestedState: (Path) -> ExpectedProjectState,
    pathToOpen: Path,
  ): (Path) -> ExpectedProjectState {
    checkOpenerIsApplicableToTargetPath(opener, pathToOpen)

    if (pathToOpen.resolve(".idea/modules.xml").exists()) {
      // At the moment valid ".idea" always wins. You cannot ignore it.
      // Even FolderProjectOpenProcessor cannot ignore it (but we wish that it could).
      return makerSuggestedState
    }

    return when (opener.mode) {
      ModeFolderAsFolder -> {
        emptyProject
      }
      ModeFileOrFolderDefault if pathToOpen.isDirectory() -> {
        emptyProject
      }
      else -> {
        makerSuggestedState
      }
    }
  }

  @CartesianTest
  @CartesianTest.MethodFactory("opener_X_ideaProjectMaker_X_attachProcessors")
  fun `open valid existing idea_dir or ipr_file`(
    opener: Opener,
    maker: IdeaProjectMaker,
    attachProcessors: AttachProcessors,
  ) = runBlocking(Dispatchers.Default) {
    Assumptions.assumeFalse(
      maker == IdeaProjectMaker.IdeaDirectory || maker == IdeaProjectMaker.EmptyIdeaDirectory,
      "Currently we don't have special handling for situation when .idea itself is opened as a project. " +
      "At the moment the behavior is to create a new .idea project inside .idea directory, and this is not the behavior that " +
      "we want to enforce through tests",
    )

    // Regardless of product (Idea vs PhpStorm), if .idea directory (ipr file) exists, but no modules, we must run configurators to add some module.
    // Maybe not fully clear why it is performed as part of project opening and silently, but it is existing behaviour.
    attachProcessors.configureAttachProcessors(disposable)

    val projectDir = tempDir.newPath("project")
    val projectFileToOpen = maker.makeProject(projectDir)
    checkOpenerIsApplicableToTargetPath(opener, projectFileToOpen)

    val expectedProjectState = calcExpectedProjectState(opener, maker, projectFileToOpen)
    openWithOpenerAndAssertProjectState(opener, projectFileToOpen, expectedProjectState(projectDir), false)
  }

  @CartesianTest
  @CartesianTest.MethodFactory("opener_X_ideaProjectMaker_X_attachProcessors")
  fun `open valid existing parent dir of idea_dir or ipr_file`(
    opener: Opener,
    maker: IdeaProjectMaker,
    attachProcessors: AttachProcessors,
  ) = runBlocking(Dispatchers.Default) {
    // Regardless of product (Idea vs PhpStorm), if .idea directory exists, but no modules, we must run configurators to add some module.
    // Maybe not fully clear why it is performed as part of project opening and silently, but it is existing behaviour.
    attachProcessors.configureAttachProcessors(disposable)

    val projectDir = tempDir.newPath("project")
    maker.makeProject(projectDir)
    checkOpenerIsApplicableToTargetPath(opener, projectDir)

    val expectedProjectState = calcExpectedProjectState(opener, maker, projectDir)

    openWithOpenerAndAssertProjectState(opener, projectDir, expectedProjectState(projectDir), false)
  }

  @CartesianTest
  @CartesianTest.MethodFactory("opener_X_attachProcessors")
  fun `open clean existing project dir`(
    opener: Opener,
    attachProcessors: AttachProcessors,
  ) = runBlocking(Dispatchers.Default) {
    attachProcessors.configureAttachProcessors(disposable)
    val projectDir = tempDir.newPath("project")
    projectDir.createDirectories()
    openWithOpenerAndAssertProjectState(opener, projectDir, opener.defaultProjectTemplateShouldBeAppliedOverride ?: true)
  }

  @Test
  @Timeout(30)
  fun `openOrImport without options keeps open-or-create behavior for clean directory`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val projectDir = tempDir.newPath("project")
    projectDir.createDirectories()

    checkDefaultProjectAsTemplate { checkDefaultProjectAsTemplateTask ->
      val project = ProjectUtil.openOrImportAsync(projectDir)
      assertThat(project).isNotNull()
      project!!.useProject { openedProject ->
        assertThatProjectContainsModules(openedProject, listOf(projectDir))
        assertThatProjectContainsRootEntities(openedProject, listOf(projectDir))
        checkDefaultProjectAsTemplateTask(openedProject, true)
      }
    }
  }

  @Test
  @Timeout(30)
  // There are existing clients in external plugins which invoke explicit `ProjectUtil.openOrImport(path, OpenProjectTask())`
  // instead of implicit `ProjectUtil.openOrImport(path)`. Their expectation is that project is opened or imported as .idea project.
  // Moreover, we have a lot of internal call sites doing exactly the same. Thus, we don't break common sense of how the API should work,
  // and cover this behavior with tests.
  // Should the default project be used in this scenario or not is a question. Probably, not using the default project does not break
  // too many things. As the default project by itself is a difficult concept for comprehension, I prefer that we use it as few as possible.
  fun `openOrImport with explicit empty task keeps open-or-create behavior for clean directory`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val projectDir = tempDir.newPath("project")
    projectDir.createDirectories()

    checkDefaultProjectAsTemplate { checkDefaultProjectAsTemplateTask ->
      val project = ProjectUtil.openOrImportAsync(projectDir, OpenProjectTask())
      assertThat(project).isNotNull()
      project!!.useProject { openedProject ->
        assertThatProjectContainsModules(openedProject, listOf(projectDir))
        assertThatProjectContainsRootEntities(openedProject, listOf(projectDir))
        checkDefaultProjectAsTemplateTask(openedProject, false)
      }
    }
  }

  @CartesianTest
  @CartesianTest.MethodFactory("opener_X_attachProcessors")
  fun `open nested existing project dir`(
    opener: Opener,
    attachProcessors: AttachProcessors,
  ) = runBlocking(Dispatchers.Default) {
    attachProcessors.configureAttachProcessors(disposable)
    val projectDir = tempDir.newPath("project")
    val subProjectDir = projectDir.resolve("subproject")
    subProjectDir.resolve(".idea").createDirectories()
    projectDir.resolve(".idea").createDirectories()
    openWithOpenerAndAssertProjectState(opener, subProjectDir, opener.defaultProjectTemplateShouldBeAppliedOverride ?: false)
  }

  @CartesianTest
  @CartesianTest.MethodFactory("opener_X_attachProcessors")
  fun `open multibuild existing project dir`(
    opener: Opener,
    attachProcessors: AttachProcessors,
  ) = runBlocking(Dispatchers.Default) {
    Assumptions.assumeTrue(
      opener.mode != ModeFolderAsProject,
      "This test does not handle ModeFolderAsProject mode yet, because `null` from SelectProjectOpenProcessorDialog" +
      " has different behavior when opening folder from CLI and from open action, and we don't want to cement this behavior in tests.",
    )

    val processorNames = ProjectOpenProcessor.EXTENSION_POINT_NAME.extensionList.map(ProjectOpenProcessor::name)
    assertThat(processorNames).`as` { "Use intellij.idea.community.main.tests as a classpath" }.containsAll(listOf("Maven", "Gradle"))
    attachProcessors.configureAttachProcessors(disposable)
    val projectDir = setupMultibuildProject()
    var suggestedProcessors: List<String>? = null
    SelectProjectOpenProcessorDialog.setTestDialog(disposable) { processor, _ ->
      suggestedProcessors = processor.map(ProjectOpenProcessor::name)
      null // do not open project (~cancel)
    }
    openWithOpenerAndAssertProjectState(opener, projectDir, opener.defaultProjectTemplateShouldBeAppliedOverride ?: false) {
      assertThat(suggestedProcessors).`as`("SelectProjectOpenProcessorDialog should not be shown").isNull()
    }
  }

  @CartesianTest
  @CartesianTest.MethodFactory("opener_X_attachProcessors")
  fun `open project then open regular file`(
    opener: Opener,
    attachProcessors: AttachProcessors,
  ) = runBlocking(Dispatchers.Default) {
    Assumptions.assumeTrue(
      opener.mode != ModeFolderAsProject && opener.mode != ModeFolderAsFolder,
      "Ignore ModeFolderAsProject/ModeFolderAsFolder, because we are checking open of regular files here, not folders",
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

    attachProcessors.configureAttachProcessors(disposable)
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

  @CartesianTest
  @CartesianTest.MethodFactory("opener_X_attachProcessors")
  fun `open project then open the the same valid existing project dir`(
    opener: Opener,
    attachProcessors: AttachProcessors,
  ) = runBlocking(Dispatchers.Default) {
    val projectDir = tempDir.newPath("project")
    projectDir.resolve(".idea").createDirectories()

    attachProcessors.configureAttachProcessors(disposable)
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
    opener: Opener,
    projectDir: Path,
    defaultProjectTemplateShouldBeApplied: Boolean,
    beforeOtherChecks: ((Project) -> Unit)? = null,
  ) {
    val expectedProjectState = calcExpectedProjectState(opener, autodetectedSingleModuleProject, projectDir)
    return openWithOpenerAndAssertProjectState(opener, projectDir,
                                               expectedProjectState(projectDir),
                                               defaultProjectTemplateShouldBeApplied,
                                               beforeOtherChecks)
  }

  private suspend fun openWithOpenerAndAssertProjectState(
    opener: Opener,
    projectFileToOpen: Path,
    expectedProjectState: ExpectedProjectState,
    defaultProjectTemplateShouldBeApplied: Boolean,
    beforeOtherChecks: ((Project) -> Unit)? = null,
  ) {
    checkDefaultProjectAsTemplate { checkDefaultProjectAsTemplateTask ->
      val project = opener.opener(projectFileToOpen)!!
      project.useProject {
        beforeOtherChecks?.invoke(project)
        assertThatProjectContainsModules(project, expectedProjectState.getExpectedModules())
        assertThatProjectContainsRootEntities(project, expectedProjectState.getExpectedRoots())
        checkDefaultProjectAsTemplateTask(project, defaultProjectTemplateShouldBeApplied)
      }
    }
  }
}

internal class Opener(
  val source: TestProjectSource,
  val mode: TestOpenMode,
  val defaultProjectTemplateShouldBeAppliedOverride: Boolean? = null,
  val opener: (Path) -> Project?,
) {
  override fun toString() = "${source.toString().substringAfter("Source")}-${mode.toString().substringAfter("Mode")}"
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
