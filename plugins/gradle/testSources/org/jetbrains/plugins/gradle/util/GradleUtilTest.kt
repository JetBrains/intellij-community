// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.mock.MockVirtualFile
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.utils.io.createFile
import org.gradle.util.GradleVersion
import org.gradle.wrapper.WrapperConfiguration
import org.jetbrains.plugins.gradle.service.GradleInstallationManager
import org.junit.Test
import java.io.File
import java.net.URI

class GradleUtilTest: UsefulTestCase() {

  private lateinit var rootDir: File

  override fun setUp() {
    super.setUp()
    rootDir = FileUtil.createTempDirectory("gradleRoot", null)
  }

  @Test
  fun `test root project detector for empty dir`() {
    assertEquals(rootDir.absolutePath, GradleUtil.determineRootProject(rootDir.absolutePath))
  }

  @Test
  fun `test root project is found from subdirectory`() {
    File(rootDir, "settings.gradle").writeText("# empty settings file")
    val subDir = File(rootDir, "sub/sub/subDir").apply { mkdirs() }
    assertEquals(rootDir.absolutePath, GradleUtil.determineRootProject(subDir.absolutePath))
  }

  @Test
  fun `test kotlin root project is found from subdirectory`() {
    File(rootDir, "settings.gradle.kts").writeText("// empty settings file in Kotlin script")
    val subDir = File(rootDir, "sub/sub/subDir").apply { mkdirs() }
    assertEquals(rootDir.absolutePath, GradleUtil.determineRootProject(subDir.absolutePath))
  }

  @Test
  fun `test root project is found from a file`() {
    File(rootDir, "settings.gradle").writeText("# empty settings file")
    val projectFile = File(rootDir, "sub/sub/subDir/build.gradle").apply {
      parentFile.mkdirs()
      writeText("// empty project file")
    }
    assertEquals(rootDir.absolutePath, GradleUtil.determineRootProject(projectFile.absolutePath))
  }

  @Test
  fun `test project without settings is found from a file`() {
    val projectFile = File(rootDir, "build.gradle").apply { writeText("// empty project file") }
    assertEquals(rootDir.absolutePath, GradleUtil.determineRootProject(projectFile.absolutePath))
  }

  @Test
  fun `test file chooser descriptor accepts kotlin scripts`() {
    val chooserDescriptor = GradleUtil.getGradleProjectFileChooserDescriptor()

    assertTrue("gradle groovy script should be selectable", chooserDescriptor.isFileSelectable(MockVirtualFile("build.gradle")))
    assertTrue("gradle kotlin script should be selectable", chooserDescriptor.isFileSelectable(MockVirtualFile("build.gradle.kts")))
  }

  @Test
  fun `test parsing Gradle distribution version`() {
    assertNull(GradleInstallationManager.parseDistributionVersion(""))
    assertNull(GradleInstallationManager.parseDistributionVersion("abc"))
    assertNull(GradleInstallationManager.parseDistributionVersion("gradle"))

    assertEquals(GradleVersion.version("5.2.1"), GradleInstallationManager.parseDistributionVersion("abc/gradle-5.2.1-bin.zip"))
    assertEquals(GradleVersion.version("5.2.1"), GradleInstallationManager.parseDistributionVersion("abc/abc-gradle-5.2.1-bin.zip"))
    assertEquals(GradleVersion.version("5.2"), GradleInstallationManager.parseDistributionVersion("abc/abc-gradle-5.2-bin.zip"))
    assertEquals(GradleVersion.version("5.2-rc-1"), GradleInstallationManager.parseDistributionVersion("abc/abc-gradle-5.2-rc-1-bin.zip"))

    assertNull(GradleInstallationManager.parseDistributionVersion("abc/gradle-unexpected-bin.zip"))
  }

  fun `test project gradle wrapper properties parsing`() {
    val expected = getTestWrapperConfiguration()
    val gradlePropertiesRoot = rootDir.resolve("gradle")
      .resolve("wrapper")
      .resolve("gradle-wrapper.properties")
      .toPath()
    NioFiles.createParentDirectories(gradlePropertiesRoot).createFile()

    GradleUtil.writeWrapperConfiguration(gradlePropertiesRoot, expected)
    val actual = GradleUtil.getWrapperConfiguration(rootDir.path)
    assertWrapperConfigurationsEquals(expected, actual!!)
  }

  fun `test gradle wrapper properties parsing`() {
    val expected = getTestWrapperConfiguration()
    val wrapperPropertiesFile = rootDir.resolve("gradle-wrapper.properties")
    wrapperPropertiesFile.createNewFile()
    val wrapperPropertiesPath = wrapperPropertiesFile.toPath()
    GradleUtil.writeWrapperConfiguration(wrapperPropertiesPath, expected)
    val actual = GradleUtil.readWrapperConfiguration(wrapperPropertiesPath)
    assertWrapperConfigurationsEquals(expected, actual!!)
  }

  @Suppress("NonAsciiCharacters")
  private fun getTestWrapperConfiguration(): WrapperConfiguration {
    val configuration = WrapperConfiguration()
    configuration.distribution = URI("https://127.0.0.1/gradle.zip")
    configuration.distributionBase = "distributionBase/üüü"
    configuration.distributionPath = "distributionPath/üüü"
    configuration.zipBase = "zipBase/ßßß"
    configuration.zipPath = "zipPath/ßßß"
    return configuration
  }

  private fun assertWrapperConfigurationsEquals(expected: WrapperConfiguration, actual: WrapperConfiguration) {
    assertEquals(expected.distribution, actual.distribution)
    assertEquals(expected.distributionBase, actual.distributionBase)
    assertEquals(expected.distributionPath, actual.distributionPath)
    assertEquals(expected.zipBase, actual.zipBase)
    assertEquals(expected.zipPath, actual.zipPath)
  }
}