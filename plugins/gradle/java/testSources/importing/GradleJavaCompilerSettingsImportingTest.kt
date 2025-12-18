// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.externalSystem.util.DEFAULT_SYNC_TIMEOUT_MS
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.pom.java.JavaRelease
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.TestObservation
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Assume
import org.junit.Test

class GradleJavaCompilerSettingsImportingTest : GradleJavaCompilerSettingsImportingTestCase() {
  @Test
  fun `test project-module compatibility replacing`() {
    createJavaGradleSubProject(
      projectSourceCompatibility = "1.6",
      projectTargetCompatibility = "1.7"
    )

    importProject()

    assertProjectLanguageLevel(LanguageLevel.JDK_1_6)
    assertModuleLanguageLevel("project", LanguageLevel.JDK_1_6)
    assertModuleLanguageLevel("project.main", LanguageLevel.JDK_1_6)
    assertModuleLanguageLevel("project.test", LanguageLevel.JDK_1_6)
    assertProjectTargetBytecodeVersion("1.7")
    assertModuleTargetBytecodeVersion("project", "1.7")
    assertModuleTargetBytecodeVersion("project.main", "1.7")
    assertModuleTargetBytecodeVersion("project.test", "1.7")

    setProjectLanguageLevel(LanguageLevel.JDK_13)
    setProjectTargetBytecodeVersion("13")

    assertProjectLanguageLevel(LanguageLevel.JDK_13)
    assertModuleLanguageLevel("project", LanguageLevel.JDK_13)
    assertModuleLanguageLevel("project.main", LanguageLevel.JDK_13)
    assertModuleLanguageLevel("project.test", LanguageLevel.JDK_13)
    assertProjectTargetBytecodeVersion("13")
    assertModuleTargetBytecodeVersion("project", "13")
    assertModuleTargetBytecodeVersion("project.main", "13")
    assertModuleTargetBytecodeVersion("project.test", "13")

    importProject()

    assertProjectLanguageLevel(LanguageLevel.JDK_1_6)
    assertModuleLanguageLevel("project", LanguageLevel.JDK_1_6)
    assertModuleLanguageLevel("project.main", LanguageLevel.JDK_1_6)
    assertModuleLanguageLevel("project.test", LanguageLevel.JDK_1_6)
    assertProjectTargetBytecodeVersion("1.7")
    assertModuleTargetBytecodeVersion("project", "1.7")
    assertModuleTargetBytecodeVersion("project.main", "1.7")
    assertModuleTargetBytecodeVersion("project.test", "1.7")
  }

  @Test
  fun `test maximum compiler settings dispersion`() {
    createJavaGradleSubProject(
      projectSourceCompatibility = "1.3",
      projectTargetCompatibility = "1.4",
      mainSourceCompatibility = "1.5",
      mainTargetCompatibility = "1.6",
      testSourceCompatibility = "1.7",
      testTargetCompatibility = "1.8"
    )
    createJavaGradleSubProject(
      "module",
      projectSourceCompatibility = "1.8",
      projectTargetCompatibility = "1.7",
      mainSourceCompatibility = "1.6",
      mainTargetCompatibility = "1.5",
      testSourceCompatibility = "1.4",
      testTargetCompatibility = "1.3"
    )
    createGradleSettingsFile("module")

    importProject()

    assertProjectLanguageLevel(LanguageLevel.JDK_1_3)
    assertModuleLanguageLevel("project", LanguageLevel.JDK_1_3)
    assertModuleLanguageLevel("project.main", LanguageLevel.JDK_1_5)
    assertModuleLanguageLevel("project.test", LanguageLevel.JDK_1_7)
    assertModuleLanguageLevel("project.module", LanguageLevel.JDK_1_8)
    assertModuleLanguageLevel("project.module.main", LanguageLevel.JDK_1_6)
    assertModuleLanguageLevel("project.module.test", LanguageLevel.JDK_1_4)
    assertProjectTargetBytecodeVersion("1.4")
    assertModuleTargetBytecodeVersion("project", "1.4")
    assertModuleTargetBytecodeVersion("project.main", "1.6")
    assertModuleTargetBytecodeVersion("project.test", "1.8")
    assertModuleTargetBytecodeVersion("project.module", "1.7")
    assertModuleTargetBytecodeVersion("project.module.main", "1.5")
    assertModuleTargetBytecodeVersion("project.module.test", "1.3")
  }

  @Test
  fun `test language level approximation`() {
    val highest = JavaRelease.getHighest()
    val nonPreview = highest.getNonPreviewLevel()
    val preview = nonPreview.getPreviewLevel()

    Assume.assumeTrue("The IDE $highest Java version doesn't have preview level",
                      preview != null)
    Assume.assumeTrue("The $currentGradleVersion doesn't support the IDE $highest Java version",
                      GradleJvmSupportMatrix.isSupported(currentGradleVersion, nonPreview.toJavaVersion()))

    createJavaGradleSubProject(
      projectSourceCompatibility = nonPreview.shortText,
      mainSourceCompatibilityEnablePreview = true,
      testSourceCompatibilityEnablePreview = true
    )

    importProject()

    assertProjectLanguageLevel(preview)
    assertModuleLanguageLevel("project", preview)
    assertModuleLanguageLevel("project.main", preview)
    assertModuleLanguageLevel("project.test", preview)

    createJavaGradleSubProject(
      "module",
      projectSourceCompatibility = nonPreview.shortText,
      mainSourceCompatibilityEnablePreview = true,
      testSourceCompatibilityEnablePreview = true
    )
    createGradleSettingsFile("module")

    importProject()

    assertProjectLanguageLevel(preview)
    assertModuleLanguageLevel("project", preview)
    assertModuleLanguageLevel("project.main", preview)
    assertModuleLanguageLevel("project.test", preview)
    assertModuleLanguageLevel("project.module", preview)
    assertModuleLanguageLevel("project.module.main", preview)
    assertModuleLanguageLevel("project.module.test", preview)

    createJavaGradleSubProject(
      "module1",
      projectSourceCompatibility = nonPreview.shortText,
      mainSourceCompatibilityEnablePreview = true,
      testSourceCompatibilityEnablePreview = false
    )
    createJavaGradleSubProject(
      "module2",
      projectSourceCompatibility = nonPreview.shortText,
      mainSourceCompatibilityEnablePreview = false,
      testSourceCompatibilityEnablePreview = true
    )
    createJavaGradleSubProject(
      "module3",
      projectSourceCompatibility = nonPreview.shortText,
      mainSourceCompatibilityEnablePreview = false,
      testSourceCompatibilityEnablePreview = false
    )
    createGradleSettingsFile("module", "module1", "module2", "module3")

    importProject()

    assertProjectLanguageLevel(nonPreview)
    assertModuleLanguageLevel("project", preview)
    assertModuleLanguageLevel("project.main", preview)
    assertModuleLanguageLevel("project.test", preview)
    assertModuleLanguageLevel("project.module", preview)
    assertModuleLanguageLevel("project.module.main", preview)
    assertModuleLanguageLevel("project.module.test", preview)
    assertModuleLanguageLevel("project.module1", nonPreview)
    assertModuleLanguageLevel("project.module1.main", preview)
    assertModuleLanguageLevel("project.module1.test", nonPreview)
    assertModuleLanguageLevel("project.module2", nonPreview)
    assertModuleLanguageLevel("project.module2.main", nonPreview)
    assertModuleLanguageLevel("project.module2.test", preview)
    assertModuleLanguageLevel("project.module3", nonPreview)
    assertModuleLanguageLevel("project.module3.main", nonPreview)
    assertModuleLanguageLevel("project.module3.test", nonPreview)
  }

  @Test
  @TargetVersions("6.7+")
  fun `test module SDK support (simple)`() {
    val javaSdkVersion = JavaSdkVersion.fromJavaVersion(gradleJvmInfo.version)!!

    createJavaGradleSubProject()

    importProject()

    assertModules("project", "project.main", "project.test")

    assertProjectSdk(javaSdkVersion)
    assertModuleSdk("project", javaSdkVersion)
    assertModuleSdk("project.main", javaSdkVersion)
    assertModuleSdk("project.test", javaSdkVersion)
    assertProjectLanguageLevel(javaSdkVersion.maxLanguageLevel)
    assertModuleLanguageLevel("project", javaSdkVersion.maxLanguageLevel)
    assertModuleLanguageLevel("project.main", javaSdkVersion.maxLanguageLevel)
    assertModuleLanguageLevel("project.test", javaSdkVersion.maxLanguageLevel)
  }

  @Test
  @TargetVersions("6.7+")
  fun `test module SDK support (two linked projects)`() {
    val javaSdkVersion = JavaSdkVersion.fromJavaVersion(gradleJvmInfo.version)!!

    createJavaGradleSubProject("project1")
    createJavaGradleSubProject("project2")

    val settings = GradleSettings.getInstance(myProject)
    settings.linkProject(GradleProjectSettings("$projectPath/project1"))
    settings.linkProject(GradleProjectSettings("$projectPath/project2"))
    ExternalSystemUtil.refreshProjects(createImportSpec())
    TestObservation.waitForConfiguration(myProject, DEFAULT_SYNC_TIMEOUT_MS)

    assertModules("project1", "project1.main", "project1.test",
                  "project2", "project2.main", "project2.test")

    assertProjectSdk(null)
    assertModuleSdk("project1", javaSdkVersion)
    assertModuleSdk("project1.main", javaSdkVersion)
    assertModuleSdk("project1.test", javaSdkVersion)
    assertModuleSdk("project2", javaSdkVersion)
    assertModuleSdk("project2.main", javaSdkVersion)
    assertModuleSdk("project2.test", javaSdkVersion)
    assertProjectLanguageLevel(JavaRelease.getHighest())
    assertModuleLanguageLevel("project1", javaSdkVersion.maxLanguageLevel)
    assertModuleLanguageLevel("project1.main", javaSdkVersion.maxLanguageLevel)
    assertModuleLanguageLevel("project1.test", javaSdkVersion.maxLanguageLevel)
    assertModuleLanguageLevel("project2", javaSdkVersion.maxLanguageLevel)
    assertModuleLanguageLevel("project2.main", javaSdkVersion.maxLanguageLevel)
    assertModuleLanguageLevel("project2.test", javaSdkVersion.maxLanguageLevel)
  }

  @Test
  @TargetVersions("6.7+")
  fun `test Java toolchain support (simple)`() {
    createJavaGradleSubProject(
      projectToolchainLanguage = 8
    )

    importProject()

    assertModules("project", "project.main", "project.test")

    assertProjectSdk(JavaSdkVersion.JDK_1_8)
    assertModuleSdk("project", JavaSdkVersion.JDK_1_8)
    assertModuleSdk("project.main", JavaSdkVersion.JDK_1_8)
    assertModuleSdk("project.test", JavaSdkVersion.JDK_1_8)
    assertProjectLanguageLevel(LanguageLevel.JDK_1_8)
    assertModuleLanguageLevel("project", LanguageLevel.JDK_1_8)
    assertModuleLanguageLevel("project.main", LanguageLevel.JDK_1_8)
    assertModuleLanguageLevel("project.test", LanguageLevel.JDK_1_8)
  }

  @Test
  @TargetVersions("6.7+")
  fun `test Java toolchain support (compiler task)`() {
    val javaSdkVersion = JavaSdkVersion.fromJavaVersion(gradleJvmInfo.version)!!

    createJavaGradleSubProject(
      mainToolchainLanguage = 8,
      testToolchainLanguage = 11
    )

    importProject()

    assertModules("project", "project.main", "project.test")

    assertProjectSdk(javaSdkVersion)
    assertModuleSdk("project", javaSdkVersion)
    assertModuleSdk("project.main", JavaSdkVersion.JDK_1_8)
    assertModuleSdk("project.test", JavaSdkVersion.JDK_11)
    assertProjectLanguageLevel(javaSdkVersion.maxLanguageLevel)
    assertModuleLanguageLevel("project", javaSdkVersion.maxLanguageLevel)
    assertModuleLanguageLevel("project.main", LanguageLevel.JDK_1_8)
    assertModuleLanguageLevel("project.test", LanguageLevel.JDK_11)
  }

  @Test
  @TargetVersions("6.7+")
  fun `test Java toolchain support (mixed)`() {
    createJavaGradleSubProject(
      projectToolchainLanguage = 17,
      mainToolchainLanguage = 8,
      testToolchainLanguage = 11
    )

    importProject()

    assertModules("project", "project.main", "project.test")

    assertProjectSdk(JavaSdkVersion.JDK_17)
    assertModuleSdk("project", JavaSdkVersion.JDK_17)
    assertModuleSdk("project.main", JavaSdkVersion.JDK_1_8)
    assertModuleSdk("project.test", JavaSdkVersion.JDK_11)
    assertProjectLanguageLevel(LanguageLevel.JDK_17)
    assertModuleLanguageLevel("project", LanguageLevel.JDK_17)
    assertModuleLanguageLevel("project.main", LanguageLevel.JDK_1_8)
    assertModuleLanguageLevel("project.test", LanguageLevel.JDK_11)
  }

  @Test
  @TargetVersions("6.7+")
  fun `test Java toolchain support (update)`() {
    createJavaGradleSubProject(
      projectToolchainLanguage = 8,
    )

    importProject()

    assertModules("project", "project.main", "project.test")

    assertProjectSdk(JavaSdkVersion.JDK_1_8)
    assertModuleSdk("project", JavaSdkVersion.JDK_1_8)
    assertModuleSdk("project.main", JavaSdkVersion.JDK_1_8)
    assertModuleSdk("project.test", JavaSdkVersion.JDK_1_8)
    assertProjectLanguageLevel(LanguageLevel.JDK_1_8)
    assertModuleLanguageLevel("project", LanguageLevel.JDK_1_8)
    assertModuleLanguageLevel("project.main", LanguageLevel.JDK_1_8)
    assertModuleLanguageLevel("project.test", LanguageLevel.JDK_1_8)

    createJavaGradleSubProject(
      projectToolchainLanguage = 11,
    )

    importProject()

    assertModules("project", "project.main", "project.test")

    assertProjectSdk(JavaSdkVersion.JDK_1_8) // Bug IDEA-258496 should be JDK_11
    assertModuleSdk("project", JavaSdkVersion.JDK_11)
    assertModuleSdk("project.main", JavaSdkVersion.JDK_11)
    assertModuleSdk("project.test", JavaSdkVersion.JDK_11)
    assertProjectLanguageLevel(LanguageLevel.JDK_11)
    assertModuleLanguageLevel("project", LanguageLevel.JDK_11)
    assertModuleLanguageLevel("project.main", LanguageLevel.JDK_11)
    assertModuleLanguageLevel("project.test", LanguageLevel.JDK_11)
  }
}