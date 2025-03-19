// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.nio.file.Path

class GradleContentRootIndexTest : GradleContentRootIndexTestCase() {

  @Test
  fun `test content root resolution for sources`() {
    val projectPath = Path.of("path/to/project")
    val externalProject = createExternalProject(projectPath)

    val mainSources = listOf(
      projectPath.resolve("src/main/java"),
      projectPath.resolve("src/main/resources")
    )
    val testSources = listOf(
      projectPath.resolve("src/test/java"),
      projectPath.resolve("src/test/resources")
    )

    val contentRootIndex = GradleContentRootIndex()
    contentRootIndex.addSourceRoots(mainSources)
    contentRootIndex.addSourceRoots(testSources)

    Assertions.assertThat(
      contentRootIndex.resolveContentRoots(externalProject, mainSources)
    ).containsExactlyInAnyOrder(
      projectPath.resolve("src/main"),
    )

    Assertions.assertThat(
      contentRootIndex.resolveContentRoots(externalProject, testSources)
    ).containsExactlyInAnyOrder(
      projectPath.resolve("src/test"),
    )
  }

  @Test
  fun `test content root resolution for custom sources`() {
    val projectPath = Path.of("path/to/project")
    val externalProject = createExternalProject(projectPath)

    val mainSources = listOf(
      projectPath.resolve("src/java"),
      projectPath.resolve("src/resources")
    )
    val testSources = listOf(
      projectPath.resolve("testSrc/java"),
      projectPath.resolve("testSrc/resources")
    )

    val contentRootIndex = GradleContentRootIndex()
    contentRootIndex.addSourceRoots(mainSources)
    contentRootIndex.addSourceRoots(testSources)

    Assertions.assertThat(
      contentRootIndex.resolveContentRoots(externalProject, mainSources)
    ).containsExactlyInAnyOrder(
      projectPath.resolve("src"),
    )

    Assertions.assertThat(
      contentRootIndex.resolveContentRoots(externalProject, testSources)
    ).containsExactlyInAnyOrder(
      projectPath.resolve("testSrc"),
    )
  }

  @Test
  fun `test content root resolution for incomplete sources`() {
    val projectPath = Path.of("path/to/project")
    val externalProject = createExternalProject(projectPath)

    run {
      val mainSources = listOf(
        projectPath.resolve("src/main/java")
      )
      val testSources = listOf(
        projectPath.resolve("src/test/java")
      )

      val contentRootIndex = GradleContentRootIndex()
      contentRootIndex.addSourceRoots(mainSources)
      contentRootIndex.addSourceRoots(testSources)

      Assertions.assertThat(
        contentRootIndex.resolveContentRoots(externalProject, mainSources)
      ).containsExactlyInAnyOrder(
        projectPath.resolve("src/main"),
      )

      Assertions.assertThat(
        contentRootIndex.resolveContentRoots(externalProject, testSources)
      ).containsExactlyInAnyOrder(
        projectPath.resolve("src/test"),
      )
    }

    run {
      val mainSources = listOf(
        projectPath.resolve("src/main/java")
      )

      val contentRootIndex = GradleContentRootIndex()
      contentRootIndex.addSourceRoots(mainSources)

      Assertions.assertThat(
        contentRootIndex.resolveContentRoots(externalProject, mainSources)
      ).containsExactlyInAnyOrder(
        projectPath.resolve("src/main"),
      )
    }
  }

  @Test
  fun `test content root resolution for incomplete external sources`() {
    val rootPath = Path.of("path/to/root")
    val externalProject = createExternalProject(rootPath.resolve("externalRoot"))

    run {
      val mainSources = listOf(
        rootPath.resolve("externalRoot/src/main/java")
      )
      val testSources = listOf(
        rootPath.resolve("externalRoot/src/test/java")
      )

      val contentRootIndex = GradleContentRootIndex()
      contentRootIndex.addSourceRoots(mainSources)
      contentRootIndex.addSourceRoots(testSources)

      Assertions.assertThat(
        contentRootIndex.resolveContentRoots(externalProject, mainSources)
      ).containsExactlyInAnyOrder(
        rootPath.resolve("externalRoot/src/main"),
      )

      Assertions.assertThat(
        contentRootIndex.resolveContentRoots(externalProject, testSources)
      ).containsExactlyInAnyOrder(
        rootPath.resolve("externalRoot/src/test"),
      )
    }

    run {
      val mainSources = listOf(
        rootPath.resolve("externalRoot/src/main/java")
      )

      val contentRootIndex = GradleContentRootIndex()
      contentRootIndex.addSourceRoots(mainSources)

      Assertions.assertThat(
        contentRootIndex.resolveContentRoots(externalProject, mainSources)
      ).containsExactlyInAnyOrder(
        rootPath.resolve("externalRoot/src/main"),
      )
    }
  }

  @Test
  fun `test content root resolution for flatten sources`() {
    val projectPath = Path.of("path/to/project")
    val externalProject = createExternalProject(projectPath)

    val mainSources = listOf(
      projectPath.resolve("src"),
      projectPath.resolve("resources")
    )
    val testSources = listOf(
      projectPath.resolve("testSrc"),
      projectPath.resolve("testResources")
    )

    val contentRootIndex = GradleContentRootIndex()
    contentRootIndex.addSourceRoots(mainSources)
    contentRootIndex.addSourceRoots(testSources)

    Assertions.assertThat(
      contentRootIndex.resolveContentRoots(externalProject, mainSources)
    ).containsExactlyInAnyOrder(
      projectPath.resolve("src"),
      projectPath.resolve("resources"),
    )

    Assertions.assertThat(
      contentRootIndex.resolveContentRoots(externalProject, testSources)
    ).containsExactlyInAnyOrder(
      projectPath.resolve("testSrc"),
      projectPath.resolve("testResources"),
    )
  }

  @Test
  fun `test content root resolution for generated sources`() {
    val projectPath = Path.of("path/to/project")
    val projectBuildPath = projectPath.resolve("build")
    val externalProject = createExternalProject(projectPath, projectBuildPath)

    val mainSources = listOf(
      projectPath.resolve("src/main/java"),
      projectPath.resolve("src/main/resources"),
      projectPath.resolve("build/generated/sources/annotationProcessor/java/main")
    )
    val testSources = listOf(
      projectPath.resolve("src/test/java"),
      projectPath.resolve("src/test/resources"),
      projectPath.resolve("build/generated/sources/annotationProcessor/java/test")
    )

    val contentRootIndex = GradleContentRootIndex()
    contentRootIndex.addSourceRoots(mainSources)
    contentRootIndex.addSourceRoots(testSources)

    Assertions.assertThat(
      contentRootIndex.resolveContentRoots(externalProject, mainSources)
    ).containsExactlyInAnyOrder(
      projectPath.resolve("src/main"),
      projectPath.resolve("build/generated/sources/annotationProcessor/java/main"),
    )

    Assertions.assertThat(
      contentRootIndex.resolveContentRoots(externalProject, testSources)
    ).containsExactlyInAnyOrder(
      projectPath.resolve("src/test"),
      projectPath.resolve("build/generated/sources/annotationProcessor/java/test"),
    )
  }

  @Test
  fun `test content root resolution for external sources`() {
    val rootPath = Path.of("path/to/root")
    val projectBuildPath = rootPath.resolve("projectRoot/build")
    val externalProject = createExternalProject(rootPath.resolve("projectRoot"), projectBuildPath)

    val mainSources = listOf(
      rootPath.resolve("projectRoot/src/main/java"),
      rootPath.resolve("projectRoot/src/main/resources"),
      rootPath.resolve("externalRoot/src/main/java"),
      rootPath.resolve("externalRoot/src/main/resources")
    )
    val testSources = listOf(
      rootPath.resolve("projectRoot/src/test/java"),
      rootPath.resolve("projectRoot/src/test/resources"),
      rootPath.resolve("externalRoot/src/test/java"),
      rootPath.resolve("externalRoot/src/test/resources")
    )

    val contentRootIndex = GradleContentRootIndex()
    contentRootIndex.addSourceRoots(mainSources)
    contentRootIndex.addSourceRoots(testSources)

    Assertions.assertThat(
      contentRootIndex.resolveContentRoots(externalProject, mainSources)
    ).containsExactlyInAnyOrder(
      rootPath.resolve("projectRoot/src/main"),
      rootPath.resolve("externalRoot/src/main")
    )

    Assertions.assertThat(
      contentRootIndex.resolveContentRoots(externalProject, testSources)
    ).containsExactlyInAnyOrder(
      rootPath.resolve("projectRoot/src/test"),
      rootPath.resolve("externalRoot/src/test"),
    )
  }

  @Test
  fun `test content root resolution for sources in multi-module project`() {
    val projectPath = Path.of("path/to/project")
    val externalProject = createExternalProject(projectPath)
    val moduleExternalProject = createExternalProject(projectPath.resolve("module"))

    val mainSources = listOf(
      projectPath.resolve("src/main/java"),
      projectPath.resolve("src/main/resources")
    )
    val testSources = listOf(
      projectPath.resolve("src/test/java"),
      projectPath.resolve("src/test/resources")
    )
    val moduleMainSources = listOf(
      projectPath.resolve("module/src/main/java"),
      projectPath.resolve("module/src/main/resources")
    )
    val moduleTestSources = listOf(
      projectPath.resolve("module/src/test/java"),
      projectPath.resolve("module/src/test/resources")
    )

    val contentRootIndex = GradleContentRootIndex()
    contentRootIndex.addSourceRoots(mainSources)
    contentRootIndex.addSourceRoots(testSources)
    contentRootIndex.addSourceRoots(moduleMainSources)
    contentRootIndex.addSourceRoots(moduleTestSources)

    Assertions.assertThat(
      contentRootIndex.resolveContentRoots(externalProject, mainSources)
    ).containsExactlyInAnyOrder(
      projectPath.resolve("src/main")
    )

    Assertions.assertThat(
      contentRootIndex.resolveContentRoots(externalProject, testSources)
    ).containsExactlyInAnyOrder(
      projectPath.resolve("src/test")
    )

    Assertions.assertThat(
      contentRootIndex.resolveContentRoots(moduleExternalProject, moduleMainSources)
    ).containsExactlyInAnyOrder(
      projectPath.resolve("module/src/main")
    )

    Assertions.assertThat(
      contentRootIndex.resolveContentRoots(moduleExternalProject, moduleTestSources)
    ).containsExactlyInAnyOrder(
      projectPath.resolve("module/src/test")
    )
  }
}