// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.cache

import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.testFramework.util.importProject
import org.junit.Test

class GradleLocalCacheTest : GradleImportingTestCase() {

  private companion object {
    private const val DEPENDENCY = "junit:junit:4.12"
    private const val DEPENDENCY_JAR = "junit-4.12.jar"
    private const val DEPENDENCY_SOURCES_JAR = "junit-4.12-sources.jar"
    private const val DEPENDENCY_CACHE_ROOT = "caches/modules-2/files-2.1/junit/junit/4.12"
    private const val DEPENDENCY_SOURCES_JAR_CACHE_PATH = "$DEPENDENCY_CACHE_ROOT/a6c32b40bf3d76eca54e3c601e5d1470c86fcdfa/" +
                                                          DEPENDENCY_SOURCES_JAR
    private const val DEPENDENCY_JAR_CACHE_PATH = "$DEPENDENCY_CACHE_ROOT/2973d150c0dc1fefe998f834810d68f278ea58ec/$DEPENDENCY_JAR"
  }

  override fun setUp() {
    super.setUp()
    removeGradleCacheEntry(DEPENDENCY_SOURCES_JAR_CACHE_PATH)
    removeGradleCacheEntry(DEPENDENCY_JAR_CACHE_PATH)
  }

  @Test
  fun `test find artifact in gradle cache by dependency notation`() {
    GradleSettings.getInstance(myProject).isDownloadSources = true
    importProject {
      withJavaPlugin()
      withIdeaPlugin()
      withMavenCentral()
      addTestImplementationDependency(DEPENDENCY)
    }
    val components = GradleLocalCacheHelper.parseCoordinates(DEPENDENCY)
      .let {
        GradleLocalCacheHelper.findArtifactComponents(it!!, gradleUserHome, setOf(LibraryPathType.BINARY, LibraryPathType.SOURCE))
      }
    assertThat(components.size).isEqualTo(2)
    assertThat(components[LibraryPathType.BINARY]).hasSize(1).first().isEqualTo(gradleUserHome.resolve(DEPENDENCY_JAR_CACHE_PATH))
    assertThat(components[LibraryPathType.SOURCE]).hasSize(1).first().isEqualTo(gradleUserHome.resolve(DEPENDENCY_SOURCES_JAR_CACHE_PATH))
  }

  @Test
  fun `test find artifact in gradle cache by adjacent artifact path`() {
    GradleSettings.getInstance(myProject).isDownloadSources = true
    importProject {
      withJavaPlugin()
      withIdeaPlugin()
      withMavenCentral()
      addTestImplementationDependency(DEPENDENCY)
    }
    val jarPath = gradleUserHome.resolve(DEPENDENCY_JAR_CACHE_PATH)
    val components = GradleLocalCacheHelper.findAdjacentComponents(jarPath, setOf(LibraryPathType.BINARY, LibraryPathType.SOURCE))
    assertThat(components[LibraryPathType.BINARY]).hasSize(1).first().isEqualTo(jarPath)
    assertThat(components[LibraryPathType.SOURCE]).hasSize(1).first().isEqualTo(gradleUserHome.resolve(DEPENDENCY_SOURCES_JAR_CACHE_PATH))
  }

  private fun removeGradleCacheEntry(cachePath: String) {
    val jarPath = gradleUserHome.resolve(cachePath)
    val cachedSource = jarPath.toFile()
    if (cachedSource.exists()) {
      if (!cachedSource.delete()) {
        throw IllegalStateException("Unable to prepare test execution environment")
      }
    }
  }
}