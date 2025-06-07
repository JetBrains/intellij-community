package org.jetbrains.plugins.gradle.service.execution

import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.VfsTestUtil
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.settings.DistributionType
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import java.nio.file.Path

class GradleDaemonJvmHelperTest : LightPlatformTestCase() {

  fun testDaemonJvmCriteriaSupported() {
    assertFalse(GradleDaemonJvmHelper.isDaemonJvmCriteriaSupported(GradleVersion.version("8.0")))
    assertTrue(GradleDaemonJvmHelper.isDaemonJvmCriteriaSupported(GradleVersion.version("8.8")))
    assertTrue(GradleDaemonJvmHelper.isDaemonJvmCriteriaSupported(GradleVersion.version("8.20")))
  }

  fun testDamonJvmVendorCriteriaSupported() {
    assertFalse(GradleDaemonJvmHelper.isDamonJvmVendorCriteriaSupported(GradleVersion.version("8.0")))
    assertTrue(GradleDaemonJvmHelper.isDamonJvmVendorCriteriaSupported(GradleVersion.version("8.10")))
    assertTrue(GradleDaemonJvmHelper.isDamonJvmVendorCriteriaSupported(GradleVersion.version("8.20")))
  }

  fun testDaemonJvmCriteriaRequired() {
    assertFalse(GradleDaemonJvmHelper.isDaemonJvmCriteriaRequired(GradleVersion.version("9.0")))
    assertFalse(GradleDaemonJvmHelper.isDaemonJvmCriteriaRequired(GradleVersion.version("10.0")))
  }

  fun testProjectNotUsingDaemonJvmCriteriaWithSupportedGradleVersion() {
    val gradleVersion = GradleVersion.version("8.10")
    val settings = GradleProjectSettings().apply {
      this.externalProjectPath = project.basePath
      this.distributionType = DistributionType.DEFAULT_WRAPPED
    }

    createDaemonJvmPropertiesFile(null)
    createWrapperPropertiesFile(gradleVersion)

    assertFalse(GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(settings))
    assertFalse(GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(Path.of(project.basePath!!), gradleVersion))
  }

  fun testProjectUsingDaemonJvmCriteriaWithUnsupportedGradleVersion() {
    val gradleVersion = GradleVersion.version("8.7")
    val settings = GradleProjectSettings().apply {
      this.externalProjectPath = project.basePath
      this.distributionType = DistributionType.DEFAULT_WRAPPED
    }

    createDaemonJvmPropertiesFile("17")
    createWrapperPropertiesFile(gradleVersion)

    assertFalse(GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(settings))
    assertFalse(GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(Path.of(project.basePath!!), gradleVersion))
  }

  fun testProjectUsingDaemonJvmCriteriaWithSupportedGradleVersion() {
    val gradleVersion = GradleVersion.version("8.10")
    val settings = GradleProjectSettings().apply {
      this.externalProjectPath = project.basePath
      this.distributionType = DistributionType.DEFAULT_WRAPPED
    }

    createDaemonJvmPropertiesFile("string version")
    createWrapperPropertiesFile(gradleVersion)

    assertTrue(GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(settings))
    assertTrue(GradleDaemonJvmHelper.isProjectUsingDaemonJvmCriteria(Path.of(project.basePath!!), gradleVersion))
  }

  private fun createDaemonJvmPropertiesFile(version: String?) {
    VfsTestUtil.createFile(project.baseDir, "gradle/gradle-daemon-jvm.properties", version?.let { "toolchainVersion=$version" }.orEmpty())
  }

  private fun createWrapperPropertiesFile(version: GradleVersion) {
    VfsTestUtil.createFile(project.baseDir, "gradle/wrapper/gradle-wrapper.properties", """
      distributionBase=PROJECT
      distributionPath=wrapper/dists
      distributionUrl=https\://services.gradle.org/distributions/gradle-${version.version}-bin.zip
      zipStoreBase=PROJECT
      zipStorePath=wrapper/dists
    """.trimIndent())
  }
}