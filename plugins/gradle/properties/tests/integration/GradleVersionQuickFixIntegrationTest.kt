// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.properties.tests.integration

import com.intellij.gradle.properties.GradleVersionQuickFix
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.findDocument
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleProjectRootFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.projectFixture
import org.jetbrains.plugins.gradle.testFramework.projectInfo.gradleProjectInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.simpleJavaRootModuleInfo
import org.jetbrains.plugins.gradle.testFramework.projectInfo.simpleSettingsFile
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule.Companion.SUPPORTED_GRADLE_VERSIONS
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Integration tests for [GradleVersionQuickFix]
 */
@TestApplication
class GradleVersionQuickFixIntegrationTest {

  private val lastTwoGradleVersions = SUPPORTED_GRADLE_VERSIONS.takeLast(2).map { GradleVersion.version(it) }
  private val sourceGradleVersion = lastTwoGradleVersions.first()
  private val targetGradleVersion = lastTwoGradleVersions.last()

  private val gradleFixture = gradleFixture(sourceGradleVersion)
  private val gradle by gradleFixture

  private val projectInfo = gradleProjectInfo(sourceGradleVersion) {
    files.withFiles { projectRoot ->
      val wrapperProperties = projectRoot.toNioPath().resolve("gradle/wrapper/gradle-wrapper.properties")
      val wrapperPropertiesText = wrapperProperties.readText().trimEnd('\n') + "\n" + CUSTOM_WRAPPER_PROPERTY + "\n"
      if (wrapperPropertiesText.lines().any { it.startsWith("retries=") }) {
        wrapperProperties.writeText(wrapperPropertiesText.replace("retries=0", CUSTOM_RETRIES_PROPERTY))
      }
      else {
        wrapperProperties.writeText(wrapperPropertiesText + CUSTOM_RETRIES_PROPERTY + "\n")
      }
    }
    simpleSettingsFile()
    simpleJavaRootModuleInfo()
  }
  private val projectRootFixture = gradleProjectRootFixture(projectInfo)
  private val projectRoot by projectRootFixture

  private val projectFixture = gradleFixture.projectFixture(projectRootFixture)
  private val project by projectFixture

  @Test
  fun `test quick fix updates wrapper version, and re-syncs the project`(): Unit = runBlocking {
    // The fixture has already opened and synced the project with the source Gradle version
    assertThat(readWrapperProperties()).contains("gradle-${sourceGradleVersion.version}-")
    assertResolvedGradleBaseVersion(sourceGradleVersion)

    gradle.withAllowedProjectSyncs {
      GradleVersionQuickFix(
        projectPath = projectRoot.toCanonicalPath(),
        gradleVersion = targetGradleVersion,
        requestImport = true,
      ).runQuickFix(project, DataContext.EMPTY_CONTEXT).await()
    }

    // The Gradle version is updated in `gradle-wrapper.properties`
    assertThat(readWrapperProperties()).contains("gradle-${targetGradleVersion.version}-")
    // The project is re-synced against the upgraded Gradle distribution
    assertResolvedGradleBaseVersion(targetGradleVersion)
  }

  @Test
  fun `test quick fix only touches distributionUrl and distributionSha256Sum properties`(): Unit = runBlocking {
    val oldProperties = readWrapperProperties()

    gradle.withAllowedProjectSyncs {
      GradleVersionQuickFix(
        projectPath = projectRoot.toCanonicalPath(),
        gradleVersion = targetGradleVersion,
        requestImport = true,
      ).runQuickFix(project, DataContext.EMPTY_CONTEXT).await()
    }

    val updatedProperties = readWrapperProperties()

    val oldLines = oldProperties.lines()
    val updatedLines = updatedProperties.lines()

    // The distributionSha256Sum line must be gone
    assertThat(updatedLines).noneMatch { it.startsWith("distributionSha256Sum=") }

    // Everything else must be identical, in the same order, except for the distributionUrl value.
    val expectedLines = oldLines
      .filterNot { it.startsWith("distributionSha256Sum=") }
      .map { line ->
        if (line.startsWith("distributionUrl=")) {
          val updatedUrlLine = updatedLines.singleOrNull { it.startsWith("distributionUrl=") }
          assertThat(updatedUrlLine)
            .withFailMessage("Expected exactly one distributionUrl= line in updated properties")
            .isNotNull()
          assertThat(updatedUrlLine).isNotEqualTo(line)
          updatedUrlLine!!
        }
        else line
      }

    assertThat(updatedLines).containsExactlyElementsOf(expectedLines)
  }

  @Test
  fun `test quick fix change to wrapper properties can be undone`(): Unit = runBlocking {
    val originalProperties = readWrapperProperties()
    assertThat(originalProperties).contains("gradle-${sourceGradleVersion.version}-")

    gradle.withAllowedProjectSyncs {
      GradleVersionQuickFix(
        projectPath = projectRoot.toCanonicalPath(),
        gradleVersion = targetGradleVersion,
        requestImport = true,
      ).runQuickFix(project, DataContext.EMPTY_CONTEXT).await()
    }

    // Sanity check: the quick fix actually modified the wrapper properties
    assertThat(readWrapperProperties()).contains("gradle-${targetGradleVersion.version}-")

    val wrapperPath = projectRoot.resolve("gradle/wrapper/gradle-wrapper.properties")
    val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(wrapperPath)
                      ?: error("gradle-wrapper.properties not found at $wrapperPath")

    val undoManager = UndoManager.getInstance(project)
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val fileEditor = FileEditorManager.getInstance(project).openFile(virtualFile, true).first()
        assertThat(undoManager.isUndoAvailable(fileEditor))
          .withFailMessage("Undo (Ctrl+Z) should be available after the quick fix modified the wrapper properties")
          .isTrue()
        undoManager.undo(fileEditor)
      }
    }

    // A single undo restores the original wrapper properties
    assertThat(readWrapperProperties()).isEqualTo(originalProperties)
  }

  private suspend fun readWrapperProperties(): String {
    val path: Path = projectRoot.resolve("gradle/wrapper/gradle-wrapper.properties")
    val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(path) ?: error("gradle-wrapper.properties not found at $path")
    return readAction { virtualFile.findDocument()?.text } ?: path.readText()
  }

  private fun assertResolvedGradleBaseVersion(expectedVersion: GradleVersion) {
    val resolvedVersion = requireNotNull(GradleLocalSettings.getInstance(project).getGradleVersion(projectRoot.toCanonicalPath())) {
      "Gradle version was not resolved for the linked project at ${projectRoot.toCanonicalPath()}"
    }
    assertThat(GradleVersion.version(resolvedVersion).baseVersion).isEqualTo(expectedVersion.baseVersion)
  }

  companion object {
    private const val CUSTOM_WRAPPER_PROPERTY = "myCustomProperty=preserved-value"
    private const val CUSTOM_RETRIES_PROPERTY = "retries=3"
  }
}
