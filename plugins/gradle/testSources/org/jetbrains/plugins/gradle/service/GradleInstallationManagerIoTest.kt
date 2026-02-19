// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.gradle.util.GradleVersion.version
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.io.BufferedReader
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.listDirectoryEntries

class GradleInstallationManagerIoTest : GradleInstallationManagerTestCase() {

  @Test
  fun testGetGradleHome(): Unit = runBlocking {
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .generate()
    )
    val actualGradleHome = GradleInstallationManager.getInstance().getGradleHomePath(myProject, projectPath)
    assertThat(calculateGradleDistributionRoot()).contains(actualGradleHome)
  }

  @Test
  fun testGetGradleJvmPath(): Unit = runBlocking {
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .generate()
    )
    val gradleJvmPath = GradleInstallationManager.getInstance().getGradleJvmPath(myProject, projectPath)
    assertEquals(Paths.get(gradleJdkHome!!), Paths.get(gradleJvmPath!!))
  }

  @Test
  fun testGetGradleVersion(): Unit = runBlocking {
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .generate()
    )
    val guessedVersion = GradleInstallationManager.getGradleVersion(calculateGradleDistributionRoot().first())
    // we have to compare base versions because "-rc" versions are located in a folder without a suffix
    assertEquals(version(gradleVersion).baseVersion, version(guessedVersion).baseVersion)
  }

  @Test
  fun testIsGradleSdkHome(): Unit = runBlocking {
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .generate()
    )
    assertThat(calculateGradleDistributionRoot()
                 .map { GradleInstallationManager.getInstance().isGradleSdkHome(myProject,it) })
      .allMatch { it }
  }

  @Test
  @TargetVersions(VersionMatcherRule.BASE_GRADLE_VERSION)
  fun testGetClassRoots(): Unit = runBlocking {
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .generate()
    )
    val actual = GradleInstallationManager.getInstance().getClassRoots(myProject, projectPath)
                  ?.stream()
                  ?.map { it.fileName.toString() }
                  ?.toList() ?: throw IllegalStateException("Gradle class roots must not be null")
    val expected = getExpectedGradleClassRoots()
    assertThat(actual).containsAll(expected)
    assertEquals(actual.size, expected.size)
  }

  private fun calculateGradleDistributionRoot(): List<Path> {
    val distributionFolder = gradleUserHome.resolve("wrapper/dists/gradle-$gradleVersion-bin/")
    val entries = distributionFolder.listDirectoryEntries()
    if (entries.size != 1) {
      println("WARN: Unexpected number of entries in the distribution folder: ${entries.size}")
      entries.forEach { println(it) }
    }
    // we don't want to calculate the hash to be able to resolve the folder by its name
    // val firstDistributionFolder = entries.first()
    return entries.map { it.resolve("gradle-${gradleVersion}") }
  }

  private fun getExpectedGradleClassRoots(): List<String> {
    return this.javaClass.classLoader
             .getResourceAsStream("gradleClassRoots.txt")
             ?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText)
             ?.split("\n")
           ?: throw IllegalArgumentException()
  }
}