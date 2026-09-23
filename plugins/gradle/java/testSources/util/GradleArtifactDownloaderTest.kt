// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createParentDirectories
import kotlin.io.path.outputStream

class GradleArtifactDownloaderTest : GradleImportingTestCase() {

  private companion object {
    private const val ARTIFACT_PATH = "repo/depGroup/depArtifact/1.0"
    private const val SOURCES_NOTATION = "depGroup:depArtifact:1.0:sources"
    private const val SOURCES_JAR = "depArtifact-1.0-sources.jar"
    private val TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(3)
  }

  @Test
  fun `test artifact resolves with the repositories of the module project`() {
    createProjectWithSubprojectRepository()

    val moduleData = CachedModuleDataFinder.getGradleModuleData(getModule("project.sub"))!!
    val future = GradleArtifactDownloader.downloadArtifact(
      myProject, "Download artifact", SOURCES_NOTATION, moduleData, GradleDependencySourceDownloaderErrorHandler.Noop
    )
    val path = PlatformTestUtil.waitForFuture(future, TIMEOUT_MILLIS)

    assertNotNull("The artifact must resolve with the repositories of the ':sub' project", path)
    assertEquals(SOURCES_JAR, path!!.fileName.toString())
  }

  @Test
  fun `test artifact does not resolve with the repositories of the root project`() {
    createProjectWithSubprojectRepository()

    val moduleData = CachedModuleDataFinder.getGradleModuleData(getModule("project.sub"))!!
    val future = GradleArtifactDownloader.downloadArtifact(
      myProject, "Download artifact", SOURCES_NOTATION, moduleData.directoryToRunTask, GradleDependencySourceDownloaderErrorHandler.Noop
    )
    val path = PlatformTestUtil.waitForFuture(future, TIMEOUT_MILLIS)

    assertNull("The root project has no repositories, so the artifact must not resolve", path)
  }

  @Test
  @TargetVersions("6.8+")
  fun `test artifact resolves with the repositories of the included build project`() {
    createSettingsFile("""
      rootProject.name = 'project'
      includeBuild 'included'
    """.trimIndent())
    createProjectSubFile("included/settings.gradle", "rootProject.name = 'included'")
    createRepositoryProject("included")
    importProject("")
    assertModules("project", "included")

    val moduleData = CachedModuleDataFinder.getGradleModuleData(getModule("included"))!!
    val future = GradleArtifactDownloader.downloadArtifact(
      myProject, "Download artifact", SOURCES_NOTATION, moduleData, GradleDependencySourceDownloaderErrorHandler.Noop
    )
    val path = PlatformTestUtil.waitForFuture(future, TIMEOUT_MILLIS)

    assertNotNull("The artifact must resolve with the repositories of the included build", path)
    assertEquals(SOURCES_JAR, path!!.fileName.toString())
  }

  private fun createProjectWithSubprojectRepository() {
    createSettingsFile("""
      rootProject.name = 'project'
      include 'sub'
    """.trimIndent())
    createRepositoryProject("sub")
    importProject("")
    assertModules("project", "project.sub")
  }

  /**
   * Creates a Gradle project in [projectDir] that has a local Maven repository with the sources of `depGroup:depArtifact:1.0`.
   */
  private fun createRepositoryProject(projectDir: String) {
    createProjectSubFile("$projectDir/build.gradle", """
      repositories {
        maven { url = uri('repo') }
      }
    """.trimIndent())
    createProjectSubFile("$projectDir/$ARTIFACT_PATH/depArtifact-1.0.pom", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0">
        <modelVersion>4.0.0</modelVersion>
        <groupId>depGroup</groupId>
        <artifactId>depArtifact</artifactId>
        <version>1.0</version>
      </project>
    """.trimIndent())
    createJar(Path.of(projectPath, projectDir, ARTIFACT_PATH, SOURCES_JAR))
  }

  private fun createJar(path: Path) {
    ZipOutputStream(path.createParentDirectories().outputStream()).use {
      it.putNextEntry(ZipEntry("depGroup/Dep.java"))
      it.write("package depGroup; public class Dep {}".toByteArray())
      it.closeEntry()
    }
  }
}
