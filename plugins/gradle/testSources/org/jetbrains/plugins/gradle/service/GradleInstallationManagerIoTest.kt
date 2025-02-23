// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Test
import java.io.BufferedReader
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries

class GradleInstallationManagerIoTest : GradleInstallationManagerTestCase() {

  override fun setUp() {
    super.setUp()
    overrideGradleUserHome("guh")
  }

  @Test
  fun testGetGradleHome() {
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .generate()
    )
    val actualGradleHome = GradleInstallationManager.getInstance().getGradleHomePath(project, projectPath)
    assertEquals(calculateGradleDistributionRoot(), actualGradleHome)
  }

  @Test
  fun testGetGradleJvmPath() {
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .generate()
    )
    val gradleJvmPath = GradleInstallationManager.getInstance().getGradleJvmPath(project, projectPath)
    assertEquals(gradleJdkHome, gradleJvmPath)
  }

  @Test
  fun testGetGradleVersion() {
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .generate()
    )
    val guessedVersion = GradleInstallationManager.getGradleVersion(calculateGradleDistributionRoot())
    assertEquals(gradleVersion, guessedVersion)
  }

  @Test
  fun testIsGradleSdkHome() {
    importProject(
      createBuildScriptBuilder()
        .withJavaPlugin()
        .generate()
    )
    assertTrue(GradleInstallationManager.getInstance().isGradleSdkHome(myProject, calculateGradleDistributionRoot()))
  }

  @Test
  @TargetVersions(VersionMatcherRule.BASE_GRADLE_VERSION)
  fun testGetClassRoots() {
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

  private fun calculateGradleDistributionRoot(): Path {
    val distributionFolder = gradleUserHome.resolve("wrapper/dists/gradle-$gradleVersion-bin/")
    val entries = distributionFolder.listDirectoryEntries()
    assertEquals(1, entries.size)
    // we don't want to calculate the hash to be able to resolve the folder by its name
    val firstDistributionFolder = entries.first()
    return firstDistributionFolder.resolve("gradle-${gradleVersion}")
  }

  private fun getExpectedGradleClassRoots(): List<String> {
    return this.javaClass.classLoader
             .getResourceAsStream("gradleClassRoots.txt")
             ?.bufferedReader(Charsets.UTF_8)?.use(BufferedReader::readText)
             ?.split("\n")
           ?: throw IllegalArgumentException()
  }
}