// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.properties

import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.io.createFile
import com.intellij.util.io.createDirectories
import com.intellij.util.io.createParentDirectories
import org.jetbrains.plugins.gradle.model.data.BuildParticipant
import org.jetbrains.plugins.gradle.properties.GradlePropertiesFile.resolveGradleProjectRoot
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * Verifies that [GradlePropertiesFile] resolves the Gradle build root from the in-memory Gradle settings
 * instead of searching the file system, and that it still falls back to the file system for unknown paths.
 */
@TestApplication
class GradleBuildRootResolutionTest {

  private val projectFixture = projectFixture()
  private val project by projectFixture

  private val rootPath by tempPathFixture()

  @Test
  fun `test build root resolution for a linked project root`() {
    val buildRoot = createBuildRoot("build")
    linkProject(buildRoot)

    assertBuildRoot(buildRoot, buildRoot)
  }

  @Test
  fun `test build root resolution for an imported sub-project`() {
    val buildRoot = createBuildRoot("build")
    val subProject = buildRoot.resolve("sub").createDirectories()
    linkProject(buildRoot, modules = setOf(subProject.toCanonicalPath()))

    assertBuildRoot(buildRoot, subProject)
  }

  @Test
  fun `test build root resolution for a sub-project that is not imported yet`() {
    val buildRoot = createBuildRoot("build")
    val subProject = buildRoot.resolve("sub").createDirectories()
    linkProject(buildRoot)

    assertBuildRoot(buildRoot, subProject)
  }

  @Test
  fun `test build root resolution prefers an included build over the composite root`() {
    val compositeRoot = createBuildRoot("composite")
    val includedBuild = createBuildRoot("composite/included")
    val includedSubProject = includedBuild.resolve("sub").createDirectories()
    linkProject(compositeRoot, participants = listOf(compositeRoot, includedBuild))

    assertBuildRoot(includedBuild, includedBuild)
    assertBuildRoot(includedBuild, includedSubProject)
    assertBuildRoot(compositeRoot, compositeRoot)
  }

  @Test
  fun `test build root resolution falls back to the file system for an unlinked project`() {
    val buildRoot = createBuildRoot("build")
    val subProject = buildRoot.resolve("sub").createDirectories()

    assertBuildRoot(buildRoot, subProject)
  }

  /**
   * The in-memory settings are authoritative: the ancestor's settings file must not win over the linked root.
   */
  @Test
  fun `test build root resolution ignores a settings file above the linked project root`() {
    val outerRoot = createBuildRoot("outer")
    val buildRoot = outerRoot.resolve("build").createDirectories()
    linkProject(buildRoot)

    assertBuildRoot(buildRoot, buildRoot)
  }

  @Test
  fun `test property paths in build root are taken as is`() {
    val buildRoot = createBuildRoot("build")
    val subProject = buildRoot.resolve("sub").createDirectories()
    linkProject(buildRoot, modules = setOf(subProject.toCanonicalPath()))

    val propertyPaths = GradlePropertiesFile.getPropertyPathsInBuildRoot(project, subProject)
    val expectedPropertyPath = subProject.resolve("gradle.properties").toAbsolutePath().normalize()
    assertEquals(expectedPropertyPath, propertyPaths.single { it.startsWith(rootPath) })
  }

  private fun createBuildRoot(relativePath: String): Path {
    val buildRoot = rootPath.resolve(relativePath)
    buildRoot.resolve("settings.gradle")
      .createParentDirectories()
      .createFile()
    return buildRoot
  }

  private fun linkProject(
    buildRoot: Path,
    modules: Set<String> = emptySet(),
    participants: List<Path> = emptyList(),
  ) {
    val projectSettings = GradleProjectSettings().apply {
      externalProjectPath = buildRoot.toCanonicalPath()
      setModules(modules)
      if (participants.isNotEmpty()) {
        compositeBuild = GradleProjectSettings.CompositeBuild().apply {
          setCompositeParticipants(participants.map { participant ->
            BuildParticipant().apply { rootPath = participant.toCanonicalPath() }
          })
        }
      }
    }
    GradleSettings.getInstance(project).linkProject(projectSettings)
  }

  private fun assertBuildRoot(expectedBuildRoot: Path, projectPath: Path) {
    assertEquals(expectedBuildRoot, resolveGradleProjectRoot(project, projectPath))
  }
}
