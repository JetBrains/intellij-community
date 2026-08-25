// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.tests.fixtures

import com.intellij.gradle.testFramework.annotations.BaseGradleVersionSource
import com.intellij.gradle.testFramework.fixtures.application.GradleImmutableProjectTestApplication
import com.intellij.gradle.testFramework.fixtures.immutableProjectFixture
import com.intellij.gradle.testFramework.projectInfo.GradleProjectInfo
import com.intellij.gradle.testFramework.projectInfo.configureSimpleJavaModuleInfo
import com.intellij.gradle.testFramework.projectInfo.gradleProjectInfo
import com.intellij.gradle.testFramework.projectInfo.simpleSettingsFile
import com.intellij.gradle.testFramework.util.appendTextAndCommit
import com.intellij.gradle.testFramework.util.getProjectFile
import com.intellij.gradle.testFramework.util.getProjectRoot
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.readText
import com.intellij.platform.testFramework.assertion.moduleAssertion.ModuleAssertions
import com.intellij.testFramework.utils.editor.commitToPsi
import com.intellij.testFramework.utils.vfs.getDocument
import com.intellij.testFramework.utils.vfs.getPsiFile
import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.params.ParameterizedClass
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText

/**
 * Checks the two guarantees of [immutableProjectFixture], neither of which is observable from a test that merely
 * passes: the [Project] instance is shared between tests, and the files a test modifies are back to their
 * original state in the next one.
 *
 * The tests are ordered in pairs: the second test of a pair observes what the first one left behind.
 */
@GradleImmutableProjectTestApplication
@ParameterizedClass
@BaseGradleVersionSource
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class GradleImmutableProjectFixtureTest(gradleVersion: GradleVersion) {

  private val project by immutableProjectFixture(gradleVersion, "immutable-project", projectInfo(gradleVersion))

  @Test
  @Order(1)
  fun `test project is materialized and can be modified`() {
    assertModules()

    val buildScript = project.getProjectFile(BUILD_SCRIPT_NAME)
    originalBuildScriptText = buildScript.readText()
    assertThat(originalBuildScriptText).doesNotContain(MODIFICATION_MARKER)

    openedProject = project

    project.appendTextAndCommit(BUILD_SCRIPT_NAME, MODIFICATION_MARKER)
    assertThat(project.getProjectFile(BUILD_SCRIPT_NAME).readText()).contains(MODIFICATION_MARKER)
  }

  @Test
  @Order(2)
  fun `test project is reused and modifications are reverted`() {
    assertProjectIsReused()

    assertThat(project.getProjectFile(BUILD_SCRIPT_NAME).readText())
      .describedAs("Files modified by a test should be reverted before the next one")
      .isEqualTo(originalBuildScriptText)

    assertModules()
  }

  @Test
  @Order(3)
  fun `test document is modified without saving`() {
    val buildScript = project.getProjectFile(BUILD_SCRIPT_NAME)
    WriteCommandAction.runWriteCommandAction(project) {
      val document = buildScript.getDocument()
      document.setText(document.text + "\n" + MODIFICATION_MARKER)
      document.commitToPsi(project)
    }
  }

  @Test
  @Order(4)
  fun `test unsaved document modifications are reverted`() {
    assertProjectIsReused()

    val buildScript = project.getProjectFile(BUILD_SCRIPT_NAME)
    assertThat(buildScript.readText()).isEqualTo(originalBuildScriptText)
    assertThat(runReadActionBlocking { buildScript.getDocument().text })
      .describedAs("Unsaved document modifications should be reverted before the next test")
      .isEqualTo(originalBuildScriptText)
    assertThat(runReadActionBlocking { buildScript.getPsiFile(project).text })
      .isEqualTo(originalBuildScriptText)
  }

  @Test
  @Order(5)
  fun `test file is modified and renamed`() {
    val buildScript = project.appendTextAndCommit(BUILD_SCRIPT_NAME, MODIFICATION_MARKER)
    runWriteActionAndWait {
      buildScript.rename(this, RENAMED_BUILD_SCRIPT_NAME)
    }
  }

  @Test
  @Order(6)
  fun `test modified and renamed file is restored`() {
    assertProjectIsReused()

    assertThat(project.getProjectFile(BUILD_SCRIPT_NAME).readText())
      .isEqualTo(originalBuildScriptText)
    assertThat(project.getProjectRoot().findChild(RENAMED_BUILD_SCRIPT_NAME))
      .describedAs("A file renamed by a test should be removed from its new location")
      .isNull()
  }

  @Test
  @Order(7)
  fun `test file is created bypassing the VFS`() {
    project.getProjectRoot().toNioPath().resolve(EXTERNAL_FILE_NAME).writeText(MODIFICATION_MARKER)
  }

  @Test
  @Order(8)
  fun `test file created bypassing the VFS is removed`() {
    assertProjectIsReused()

    assertThat(project.getProjectRoot().toNioPath().resolve(EXTERNAL_FILE_NAME).exists())
      .describedAs("A file created bypassing the VFS should be removed before the next test")
      .isFalse()
  }

  @Test
  @Order(9)
  fun `test directory is created`() {
    runWriteActionAndWait {
      project.getProjectRoot()
        .createChildDirectory(this, CREATED_DIRECTORY_NAME)
        .createChildData(this, EXTERNAL_FILE_NAME)
    }
  }

  @Test
  @Order(10)
  fun `test created directory is removed`() {
    assertProjectIsReused()

    assertThat(project.getProjectRoot().toNioPath().resolve(CREATED_DIRECTORY_NAME))
      .describedAs("A directory created by a test should be removed before the next test")
      .doesNotExist()
  }

  @Test
  @Order(11)
  fun `test directory is deleted`() {
    val directory = project.getProjectRoot().findFileByRelativePath(SOURCE_DIRECTORY_PATH)
    assertThat(directory).isNotNull()
    runWriteActionAndWait {
      directory!!.delete(this)
    }
  }

  @Test
  @Order(12)
  fun `test deleted directory is restored`() {
    assertProjectIsReused()

    assertThat(project.getProjectRoot().toNioPath().resolve(SOURCE_DIRECTORY_PATH))
      .describedAs("A directory deleted by a test should be restored before the next test")
      .isDirectory()
  }

  @Test
  @Order(13)
  fun `test directory is replaced by file`() {
    val directory = project.getProjectRoot().findFileByRelativePath(SOURCE_DIRECTORY_PATH)
    assertThat(directory).isNotNull()
    runWriteActionAndWait {
      val parent = directory!!.parent
      directory.delete(this)
      parent.createChildData(this, directory.name)
    }
  }

  @Test
  @Order(14)
  fun `test directory replaced by file is restored`() {
    assertProjectIsReused()

    assertThat(project.getProjectRoot().toNioPath().resolve(SOURCE_DIRECTORY_PATH))
      .describedAs("A directory that a test replaced by a file should be restored before the next test")
      .isDirectory()
  }

  @Test
  @Order(15)
  fun `test file is replaced by directory`() {
    val file = project.getProjectFile(ORIGINAL_FILE_NAME)
    runWriteActionAndWait {
      val parent = file.parent
      file.delete(this)
      parent.createChildDirectory(this, ORIGINAL_FILE_NAME)
        .createChildData(this, EXTERNAL_FILE_NAME)
    }
  }

  @Test
  @Order(16)
  fun `test file replaced by directory is restored`() {
    assertProjectIsReused()

    val path = project.getProjectRoot().toNioPath().resolve(ORIGINAL_FILE_NAME)
    assertThat(path)
      .describedAs("A file that a test replaced by a directory should be restored before the next test")
      .isRegularFile()
      .hasContent(ORIGINAL_FILE_TEXT)
  }

  @Test
  @Order(17)
  fun `test file is renamed with different case`() {
    val file = project.getProjectFile(ORIGINAL_FILE_NAME)
    runWriteActionAndWait {
      file.rename(this, ORIGINAL_FILE_NAME.uppercase())
    }
  }

  /**
   * Compares the exact names, because a path check ignores the case on Windows.
   */
  @Test
  @Order(18)
  fun `test file renamed with different case is restored`() {
    assertProjectIsReused()

    val projectRoot = project.getProjectRoot()
    assertThat(projectRoot.toNioPath().listDirectoryEntries().map { it.name })
      .contains(ORIGINAL_FILE_NAME)
      .doesNotContain(ORIGINAL_FILE_NAME.uppercase())
    assertThat(projectRoot.children.map { it.name })
      .contains(ORIGINAL_FILE_NAME)
      .doesNotContain(ORIGINAL_FILE_NAME.uppercase())
    assertThat(project.getProjectFile(ORIGINAL_FILE_NAME).readText())
      .isEqualTo(ORIGINAL_FILE_TEXT)
  }

  @Test
  @Order(19)
  fun `test existing file is changed bypassing the VFS`() {
    project.getProjectRoot().toNioPath().resolve(ORIGINAL_FILE_NAME).writeText(MODIFICATION_MARKER)
  }

  @Test
  @Order(20)
  fun `test existing file changed bypassing the VFS is restored`() {
    assertProjectIsReused()

    assertThat(project.getProjectRoot().toNioPath().resolve(ORIGINAL_FILE_NAME))
      .describedAs("A file changed bypassing the VFS should be restored before the next test")
      .hasContent(ORIGINAL_FILE_TEXT)
    assertThat(project.getProjectFile(ORIGINAL_FILE_NAME).readText())
      .isEqualTo(ORIGINAL_FILE_TEXT)
  }

  @Test
  @Order(21)
  fun `test existing file is deleted bypassing the VFS`() {
    project.getProjectRoot().toNioPath().resolve(ORIGINAL_FILE_NAME).deleteExisting()
  }

  @Test
  @Order(22)
  fun `test existing file deleted bypassing the VFS is restored`() {
    assertProjectIsReused()

    assertThat(project.getProjectRoot().toNioPath().resolve(ORIGINAL_FILE_NAME))
      .describedAs("A file deleted bypassing the VFS should be restored before the next test")
      .isRegularFile()
      .hasContent(ORIGINAL_FILE_TEXT)
    assertThat(project.getProjectFile(ORIGINAL_FILE_NAME).readText())
      .isEqualTo(ORIGINAL_FILE_TEXT)
  }

  @Test
  @Order(23)
  fun `test directory with file is moved`() {
    val projectRoot = project.getProjectRoot()
    val directory = projectRoot.findChild(MOVED_DIRECTORY_NAME)
    val newParent = projectRoot.findChild("src")
    assertThat(directory).isNotNull()
    assertThat(newParent).isNotNull()
    runWriteActionAndWait {
      directory!!.move(this, newParent!!)
    }
  }

  @Test
  @Order(24)
  fun `test moved directory with file is restored`() {
    assertProjectIsReused()

    val projectRoot = project.getProjectRoot().toNioPath()
    assertThat(projectRoot.resolve(MOVED_DIRECTORY_NAME).resolve(ORIGINAL_FILE_NAME))
      .describedAs("A directory moved by a test should be restored with its files before the next test")
      .isRegularFile()
      .hasContent(ORIGINAL_FILE_TEXT)
    assertThat(projectRoot.resolve("src").resolve(MOVED_DIRECTORY_NAME))
      .describedAs("A directory moved by a test should be removed from its new location")
      .doesNotExist()
  }

  /**
   * JUnit runs a nested class after the tests of its outer class.
   */
  @Nested
  inner class NestedClassTest {

    @Test
    fun `test nested class uses the project of the outer class`() {
      assertProjectIsReused()
      assertModules()
    }

    /**
     * Runs before the leak trackers of the nested class. They run after the project is closed.
     */
    @AfterEach
    fun forgetOpenedProject() {
      openedProject = null
    }
  }

  private fun assertProjectIsReused() {
    assertThat(project)
      .describedAs("The project should be reused instead of being reopened for every test")
      .isSameAs(openedProject)
  }

  /**
   * The modules only exist if the project was really imported by Gradle, which is what the reuse is about.
   */
  private fun assertModules() {
    ModuleAssertions.assertModules(project, "project", "project.main", "project.test")
  }

  companion object {

    private const val BUILD_SCRIPT_NAME = "build.gradle.kts"
    private const val RENAMED_BUILD_SCRIPT_NAME = "build2.gradle.kts"
    private const val EXTERNAL_FILE_NAME = "external.txt"
    private const val MODIFICATION_MARKER = "// modified by the test"
    private const val CREATED_DIRECTORY_NAME = "created"
    private const val SOURCE_DIRECTORY_PATH = "src/main/java"
    private const val MOVED_DIRECTORY_NAME = "moved"
    private const val ORIGINAL_FILE_NAME = "original.txt"
    private const val ORIGINAL_FILE_TEXT = "original"

    private lateinit var originalBuildScriptText: String
    private var openedProject: Project? = null

    /**
     * A simple Java project with an empty source directory, a plain file, and a directory with a file.
   * A test can delete, replace or move them.
     */
    private fun projectInfo(gradleVersion: GradleVersion): GradleProjectInfo {
      return gradleProjectInfo(gradleVersion) {
        simpleSettingsFile()
        rootModuleInfo {
          configureSimpleJavaModuleInfo()
          files.withDirectory(SOURCE_DIRECTORY_PATH)
          files.withFile(ORIGINAL_FILE_NAME, ORIGINAL_FILE_TEXT)
          files.withFile("$MOVED_DIRECTORY_NAME/$ORIGINAL_FILE_NAME", ORIGINAL_FILE_TEXT)
        }
      }
    }

    /**
     * Runs before the leak trackers of [GradleImmutableProjectTestApplication], which would otherwise find the
     * closed project held by this field.
     */
    @AfterAll
    @JvmStatic
    fun forgetOpenedProject() {
      openedProject = null
    }
  }
}
