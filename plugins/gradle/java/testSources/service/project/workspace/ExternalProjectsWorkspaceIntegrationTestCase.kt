// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project.workspace

import com.intellij.openapi.application.readAction
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.externalSystem.autolink.UnlinkedProjectStartupActivity
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.refreshAndFindVirtualDirectory
import com.intellij.platform.backend.observation.ActivityKey
import com.intellij.platform.backend.observation.trackActivity
import com.intellij.platform.externalSystem.testFramework.ExternalSystemImportingTestCase
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.TestObservation
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.openProjectAsync
import com.intellij.testFramework.withProjectAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.gradle.util.GradleVersion
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder.Companion.buildScript
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder.Companion.getBuildScriptName
import org.jetbrains.plugins.gradle.service.project.wizard.util.generateGradleWrapper
import org.jetbrains.plugins.gradle.service.project.workspace.util.MavenPomBuilder
import org.jetbrains.plugins.gradle.service.project.workspace.util.MavenPomBuilder.Companion.mavenPom
import org.jetbrains.plugins.gradle.service.project.workspace.util.MavenSettingsBuilder
import org.jetbrains.plugins.gradle.service.project.workspace.util.MavenSettingsBuilder.Companion.mavenSettings
import org.jetbrains.plugins.gradle.testFramework.fixtures.impl.GradleJvmTestFixture
import org.jetbrains.plugins.gradle.testFramework.util.DEFAULT_SYNC_TIMEOUT
import org.jetbrains.plugins.gradle.tooling.JavaVersionRestriction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import java.io.ByteArrayOutputStream
import java.nio.file.Path
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

  private lateinit var gradleJvmFixture: GradleJvmTestFixture

  @BeforeEach
  fun setUp() {
    gradleJvmFixture = GradleJvmTestFixture(gradleVersion, javaVersion)
    gradleJvmFixture.setUp()
    gradleJvmFixture.installProjectSettingsConfigurator()

    AutoImportProjectTracker.enableAutoReloadInTests(testDisposable)
    AutoImportProjectTracker.enableAsyncAutoReloadInTests(testDisposable)
    ExternalSystemImportingTestCase.installExecutionOutputPrinter(testDisposable)
  }

  @AfterEach
  fun tearDown() {
    runAll(
      { gradleJvmFixture.tearDown() }
    )
  }

  suspend fun openProject(relativePath: String): Project {
    val projectRoot = testRoot.resolve(relativePath)
    return awaitOpenProjectConfiguration {
      openProjectAsync(projectRoot, UnlinkedProjectStartupActivity())
    }
  }

  suspend fun linkProject(project: Project, relativePath: String) {
    val projectPath = testRoot.resolve(relativePath)
    val extensions = ExternalSystemUnlinkedProjectAware.EP_NAME.extensionList
      .filter { it.hasBuildFiles(project, projectPath) }
    Assertions.assertEquals(1, extensions.size) {
      "Cannot find applicable external system to link project in $projectPath"
    }
    awaitProjectConfiguration(project) {
      extensions.single().linkAndLoadProjectAsync(project, projectPath.toCanonicalPath())
    }
  }

  suspend fun unlinkProject(project: Project, relativePath: String) {
    val projectPath = testRoot.resolve(relativePath)
    val extensions = ExternalSystemUnlinkedProjectAware.EP_NAME.extensionList
      .filter { it.isLinkedProject(project, projectPath.toCanonicalPath()) }
    Assertions.assertEquals(1, extensions.size) {
      "Cannot find applicable external system to link project in $projectPath"
    }
    awaitProjectConfiguration(project) {
      extensions.single().unlinkProject(project, projectPath.toCanonicalPath())
    }
  }

  private suspend fun awaitOpenProjectConfiguration(openProject: suspend () -> Project): Project {
    return openProject().withProjectAsync { project ->
      TestObservation.awaitConfiguration(DEFAULT_SYNC_TIMEOUT, project)
      IndexingTestUtil.suspendUntilIndexesAreReady(project)
    }
  }

  private suspend fun <R> awaitProjectConfiguration(project: Project, action: suspend () -> R): R {
    return project.trackActivity(TestProjectConfigurationActivityKey, action).also {
      TestObservation.awaitConfiguration(DEFAULT_SYNC_TIMEOUT, project)
      IndexingTestUtil.suspendUntilIndexesAreReady(project)
    }
  }

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

  suspend fun createMavenSettingsFile(relativePath: String, configure: MavenSettingsBuilder.() -> Unit): Path {
    return withContext(Dispatchers.IO) {
      testRoot.resolve(relativePath).resolve(MavenConstants.SETTINGS_XML)
        .createParentDirectories().createFile()
        .apply { writeText(mavenSettings(configure)) }
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
      generateGradleWrapper(testRoot.resolve(relativePath), gradleVersion)
    }
  }

  suspend fun createGradleBuildFile(relativePath: String, configure: GradleBuildScriptBuilder<*>.() -> Unit) {
    withContext(Dispatchers.IO) {
      testRoot.resolve(relativePath).resolve(getBuildScriptName(GradleDsl.KOTLIN))
        .createParentDirectories().createFile()
        .writeText(buildScript(gradleVersion, GradleDsl.KOTLIN, configure))
    }
  }

  fun <Self : GradleBuildScriptBuilder<*>> Self.withMavenLocal(relativePath: String): Self = apply {
    withRepository {
      mavenLocal(testRoot.resolve(relativePath).normalize().toUri().toString())
    }
  }

  private object TestProjectConfigurationActivityKey : ActivityKey {
    override val presentableName: @Nls String
      get() = "The test multi-project configuration"
  }

  companion object {

    private suspend fun ExternalSystemUnlinkedProjectAware.hasBuildFiles(project: Project, projectPath: Path): Boolean {
      val projectRoot = projectPath.refreshAndFindVirtualDirectory() ?: return false
      return readAction {
        projectRoot.isValid && projectRoot.children.any { isBuildFile(project, it) }
      }
    }

    private fun createEmptyJarContent(): ByteArray {
      return ByteArrayOutputStream().use { output ->
        JarOutputStream(output).use {}
        output.toByteArray()
      }
    }
  }
}