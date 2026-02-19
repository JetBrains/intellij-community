// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.workspace

import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.testFramework.fixtures.multiProjectFixture
import com.intellij.openapi.project.Project
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gradle.util.GradleVersion
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.service.project.workspace.util.MavenPomBuilder
import org.jetbrains.plugins.gradle.service.project.workspace.util.MavenPomBuilder.Companion.mavenPom
import org.jetbrains.plugins.gradle.service.project.workspace.util.MavenSettingsBuilder
import org.jetbrains.plugins.gradle.service.project.workspace.util.MavenSettingsBuilder.Companion.mavenSettings
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleJvmFixture
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createGradleWrapper
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction
import org.junit.jupiter.api.BeforeEach
import java.io.ByteArrayOutputStream
import java.util.jar.JarOutputStream
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

@TestApplication
abstract class ExternalProjectsWorkspaceIntegrationTestCase {

  private val gradleVersion = GradleVersion.current()
  private val javaVersion = JavaVersionRestriction.NO

  val testRoot by tempPathFixture()

  private val testDisposable by disposableFixture()

  private val gradleJvmFixture by gradleJvmFixture(gradleVersion, javaVersion)

  private val multiProjectFixture by multiProjectFixture()

  @BeforeEach
  fun setUp() {
    gradleJvmFixture.installProjectSettingsConfigurator(testDisposable)
    AutoImportProjectTracker.enableAutoReloadInTests(testDisposable)
    AutoImportProjectTracker.enableAsyncAutoReloadInTests(testDisposable)
    ExternalSystemImportingTestCase.installExecutionOutputPrinter(testDisposable)
  }

  suspend fun openProject(relativePath: String): Project =
    multiProjectFixture.openProject(testRoot.resolve(relativePath))

  suspend fun linkProject(project: Project, relativePath: String, systemId: ProjectSystemId) =
    multiProjectFixture.linkProject(project, testRoot.resolve(relativePath), systemId)

  suspend fun unlinkProject(project: Project, relativePath: String, systemId: ProjectSystemId) =
    multiProjectFixture.unlinkProject(project, testRoot.resolve(relativePath), systemId)

  suspend fun createMavenLibrary(relativePath: String, coordinates: String, configure: MavenPomBuilder.() -> Unit = {}) {
    withContext(Dispatchers.IO) {
      val (groupId, artifactId, version) = coordinates.split(":")
      val groupPath = groupId.replace(".", "/")
      val mavenRepositoryRoot = testRoot.resolve(relativePath)
      val mavenLibraryRoot = mavenRepositoryRoot.resolve("$groupPath/$artifactId/$version")
      mavenLibraryRoot.resolve("$artifactId-$version.jar")
        .createParentDirectories().createFile()
        .writeBytes(createEmptyJarContent())
      mavenLibraryRoot.resolve("$artifactId-$version.pom")
        .createParentDirectories().createFile()
        .writeText(mavenPom(coordinates, configure))
    }
  }

  suspend fun createMavenConfigFile(relativePath: String, content: String) {
    withContext(Dispatchers.IO) {
      testRoot.resolve(relativePath).resolve(MavenConstants.MAVEN_CONFIG_RELATIVE_PATH)
        .createParentDirectories().createFile()
        .writeText(content)
    }
  }

  suspend fun createMavenSettingsFile(relativePath: String, configure: MavenSettingsBuilder.() -> Unit) {
    withContext(Dispatchers.IO) {
      testRoot.resolve(relativePath).resolve(MavenConstants.SETTINGS_XML)
        .createParentDirectories().createFile()
        .writeText(mavenSettings(configure))
    }
  }

  suspend fun createMavenPomFile(relativePath: String, coordinates: String, configure: MavenPomBuilder.() -> Unit = {}) {
    withContext(Dispatchers.IO) {
      testRoot.resolve(relativePath).resolve(MavenConstants.POM_XML)
        .createParentDirectories().createFile()
        .writeText(mavenPom(coordinates, configure))
    }
  }

  suspend fun createGradleWrapper(relativePath: String) {
    withContext(Dispatchers.IO) {
      testRoot.resolve(relativePath)
        .createGradleWrapper(gradleVersion)
    }
  }

  suspend fun createGradleBuildFile(relativePath: String, configure: GradleBuildScriptBuilder<*>.() -> Unit) {
    withContext(Dispatchers.IO) {
      testRoot.resolve(relativePath)
        .createBuildFile(gradleVersion, configure = configure)
    }
  }

  fun <Self : GradleBuildScriptBuilder<*>> Self.withMavenLocal(relativePath: String): Self = apply {
    withRepository {
      mavenLocal(testRoot.resolve(relativePath).normalize().toUri().toString())
    }
  }

  companion object {

    private fun createEmptyJarContent(): ByteArray {
      return ByteArrayOutputStream().use { output ->
        JarOutputStream(output).use {}
        output.toByteArray()
      }
    }
  }
}