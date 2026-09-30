// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import org.jetbrains.plugins.gradle.importing.BuildViewMessagesImportingTestCase
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverExtension
import org.junit.Test
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createParentDirectories
import kotlin.io.path.outputStream

/**
 * Tests that the Gradle sync attaches the IntelliJ Platform sources with [com.intellij.devkit.gradle.tooling.IntelliJPlatformAuxiliaryArtifactProvider].
 *
 * The PhpStorm and WebStorm dependencies get the sources of IntelliJ IDEA Community (`com.jetbrains.intellij.idea:ideaIC`).
 */
class IntelliJPlatformAuxiliaryArtifactProviderTest : BuildViewMessagesImportingTestCase() {

  private companion object {
    private const val VERSION = "2024.1"
    private const val PHPSTORM = "com.jetbrains.intellij.phpstorm:phpstorm:$VERSION"
    private const val WEBSTORM = "com.jetbrains.intellij.webstorm:webstorm:$VERSION"
    private const val SOURCES_JAR = "ideaIC-$VERSION-sources.jar"
  }

  override fun setUp() {
    super.setUp()
    // The test application does not load the DevKit content module, so its resolver extension is registered here.
    GradleProjectResolverExtension.EP_NAME.point.registerExtension(DevKitGradleProjectResolverExtension(), testRootDisposable)
  }

  @Test
  fun `test sources are attached during sync`() {
    createArtifact("com.jetbrains.intellij.phpstorm", "phpstorm", "")
    createArtifact("com.jetbrains.intellij.idea", "ideaIC", "-sources")

    importProjectWithDependencies(PHPSTORM)

    val sourceUrls = getPhpStormSourceUrls()
    assertTrue("The sources of '$PHPSTORM' must contain '$SOURCES_JAR': $sourceUrls",
               sourceUrls.any { it.endsWith("$SOURCES_JAR!/") })
  }

  @Test
  fun `test missing sources are reported`() {
    createArtifact("com.jetbrains.intellij.phpstorm", "phpstorm", "")
    createArtifact("com.jetbrains.intellij.webstorm", "webstorm", "")

    importProjectWithDependencies(PHPSTORM, WEBSTORM)

    assertTrue(getPhpStormSourceUrls().none { it.contains(SOURCES_JAR) })
  }

  private fun importProjectWithDependencies(vararg dependencies: String) {
    createSettingsFile("rootProject.name = 'project'")
    importProject("""
      apply plugin: 'java'
      apply plugin: 'idea'
      idea.module.downloadSources = true
      repositories {
        maven { url = uri('repo') }
      }
      dependencies {
        ${dependencies.joinToString("\n") { "implementation '$it'" }}
      }
    """.trimIndent())
  }

  private fun getPhpStormSourceUrls(): List<String> {
    val library = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject).getLibraryByName("Gradle: $PHPSTORM")
    assertNotNull("The library of '$PHPSTORM' must exist", library)
    return library!!.getUrls(OrderRootType.SOURCES).toList()
  }

  /**
   * Creates a POM and a jar with the [classifier] suffix for `[group]:[artifact]:[VERSION]` in the local Maven repository.
   */
  private fun createArtifact(group: String, artifact: String, classifier: String) {
    val directory = "repo/${group.replace('.', '/')}/$artifact/$VERSION"
    createProjectSubFile("$directory/$artifact-$VERSION.pom", """
      <?xml version="1.0" encoding="UTF-8"?>
      <project xmlns="http://maven.apache.org/POM/4.0.0">
        <modelVersion>4.0.0</modelVersion>
        <groupId>$group</groupId>
        <artifactId>$artifact</artifactId>
        <version>$VERSION</version>
      </project>
    """.trimIndent())
    val jar = Path.of(projectPath, directory, "$artifact-$VERSION$classifier.jar")
    ZipOutputStream(jar.createParentDirectories().outputStream()).use {
      it.putNextEntry(ZipEntry("$artifact/Api.java"))
      it.write("package $artifact; public class Api {}".toByteArray())
      it.closeEntry()
    }
  }
}
