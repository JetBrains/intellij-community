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

    val mainSources = createSources(
      projectPath.resolve("src/main/java"),
      projectPath.resolve("src/main/resources"),
    )
    val testSources = createSources(
      projectPath.resolve("src/test/java"),
      projectPath.resolve("src/test/resources"),
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

    val mainSources = createSources(
      projectPath.resolve("src/java"),
      projectPath.resolve("src/resources"),
    )
    val testSources = createSources(
      projectPath.resolve("testSrc/java"),
      projectPath.resolve("testSrc/resources"),
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
      val mainSources = createSources(
        projectPath.resolve("src/main/java"),
      )
      val testSources = createSources(
        projectPath.resolve("src/test/java"),
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
      val mainSources = createSources(
        projectPath.resolve("src/main/java"),
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
  fun `test content root resolution for flatten sources`() {
    val projectPath = Path.of("path/to/project")
    val externalProject = createExternalProject(projectPath)

    val mainSources = createSources(
      projectPath.resolve("src"),
      projectPath.resolve("resources"),
    )
    val testSources = createSources(
      projectPath.resolve("testSrc"),
      projectPath.resolve("testResources"),
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

    val mainSources = createSources(
      projectPath.resolve("src/main/java"),
      projectPath.resolve("src/main/resources"),

      projectPath.resolve("build/generated/sources/annotationProcessor/java/main"),
    )
    val testSources = createSources(
      projectPath.resolve("src/test/java"),
      projectPath.resolve("src/test/resources"),

      projectPath.resolve("build/generated/sources/annotationProcessor/java/test"),
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
    val projectPath = Path.of("path/to/project")
    val externalPath = Path.of("path/to/external")
    val projectBuildPath = projectPath.resolve("build")
    val externalProject = createExternalProject(projectPath, projectBuildPath)

    val mainSources = createSources(
      projectPath.resolve("src/main/java"),
      projectPath.resolve("src/main/resources"),

      externalPath.resolve("src/main/java"),
      externalPath.resolve("src/main/resources"),
    )
    val testSources = createSources(
      projectPath.resolve("src/test/java"),
      projectPath.resolve("src/test/resources"),

      externalPath.resolve("src/test/java"),
      externalPath.resolve("src/test/resources"),
    )

    val contentRootIndex = GradleContentRootIndex()
    contentRootIndex.addSourceRoots(mainSources)
    contentRootIndex.addSourceRoots(testSources)

    Assertions.assertThat(
      contentRootIndex.resolveContentRoots(externalProject, mainSources)
    ).containsExactlyInAnyOrder(
      projectPath.resolve("src/main"),

      externalPath.resolve("src/main/java"),
      externalPath.resolve("src/main/resources"),
    )

    Assertions.assertThat(
      contentRootIndex.resolveContentRoots(externalProject, testSources)
    ).containsExactlyInAnyOrder(
      projectPath.resolve("src/test"),

      externalPath.resolve("src/test/java"),
      externalPath.resolve("src/test/resources"),
    )
  }

  @Test
  fun `test content root resolution for sources in multi-module project`() {
    val projectPath = Path.of("path/to/project")
    val externalProject = createExternalProject(projectPath)
    val moduleExternalProject = createExternalProject(projectPath.resolve("module"))

    val mainSources = createSources(
      projectPath.resolve("src/main/java"),
      projectPath.resolve("src/main/resources"),
    )
    val testSources = createSources(
      projectPath.resolve("src/test/java"),
      projectPath.resolve("src/test/resources"),
    )
    val moduleMainSources = createSources(
      projectPath.resolve("module/src/main/java"),
      projectPath.resolve("module/src/main/resources"),
    )
    val moduleTestSources = createSources(
      projectPath.resolve("module/src/test/java"),
      projectPath.resolve("module/src/test/resources"),
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