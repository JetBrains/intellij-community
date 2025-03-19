// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.pom.java.LanguageLevel
import com.intellij.util.SystemProperties
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.jetbrains.plugins.gradle.testFramework.util.importProject
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.junit.Assume
import org.junit.Ignore
import org.junit.Test

class GradleJavaCompilerSettingsImportingTest : GradleJavaCompilerSettingsImportingTestCase() {
  @Test
  fun `test project-module compatibility replacing`() {
    createJavaGradleSubProject(
      projectSourceCompatibility = "1.6",
      projectTargetCompatibility = "1.7"
    )
    importProject()
    assertLanguageLevels(LanguageLevel.JDK_1_6, "project", "project.main", "project.test")
    assertTargetBytecodeVersions("1.7", "project", "project.main", "project.test")

    setProjectLanguageLevel(LanguageLevel.JDK_13)
    setProjectTargetBytecodeVersion("13")
    assertLanguageLevels(LanguageLevel.JDK_13, "project", "project.main", "project.test")
    assertTargetBytecodeVersions("13", "project", "project.main", "project.test")

    importProject()
    assertLanguageLevels(LanguageLevel.JDK_1_6, "project", "project.main", "project.test")
    assertTargetBytecodeVersions("1.7", "project", "project.main", "project.test")
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
    val nonPreviewLevel = LanguageLevel.HIGHEST
    val preview = LanguageLevel.values()[LanguageLevel.HIGHEST.ordinal + 1]
    val javaVersion = nonPreviewLevel.toJavaVersion()
    val feature = javaVersion.feature

    Assume.assumeTrue(GradleJvmSupportMatrix.isSupported(currentGradleVersion, javaVersion))

    createJavaGradleSubProject(
      projectSourceCompatibility = "$feature",
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
      projectSourceCompatibility = "$feature",
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
      projectSourceCompatibility = "$feature",
      mainSourceCompatibilityEnablePreview = true,
      testSourceCompatibilityEnablePreview = false
    )
    createJavaGradleSubProject(
      "module2",
      projectSourceCompatibility = "$feature",
      mainSourceCompatibilityEnablePreview = false,
      testSourceCompatibilityEnablePreview = true
    )
    createJavaGradleSubProject(
      "module3",
      projectSourceCompatibility = "$feature",
      mainSourceCompatibilityEnablePreview = false,
      testSourceCompatibilityEnablePreview = false
    )
    createGradleSettingsFile("module", "module1", "module2", "module3", "module4")
    importProject()
    assertProjectLanguageLevel(nonPreviewLevel)
    assertModuleLanguageLevel("project", preview)
    assertModuleLanguageLevel("project.main", preview)
    assertModuleLanguageLevel("project.test", preview)
    assertModuleLanguageLevel("project.module", preview)
    assertModuleLanguageLevel("project.module.main", preview)
    assertModuleLanguageLevel("project.module.test", preview)
    assertModuleLanguageLevel("project.module1", nonPreviewLevel)
    assertModuleLanguageLevel("project.module1.main", preview)
    assertModuleLanguageLevel("project.module1.test", nonPreviewLevel)
    assertModuleLanguageLevel("project.module2", nonPreviewLevel)
    assertModuleLanguageLevel("project.module2.main", nonPreviewLevel)
    assertModuleLanguageLevel("project.module2.test", preview)
    assertModuleLanguageLevel("project.module3", nonPreviewLevel)
    assertModuleLanguageLevel("project.module3.main", nonPreviewLevel)
    assertModuleLanguageLevel("project.module3.test", nonPreviewLevel)
  }

  @Test
  @Ignore // the test is too slow: it downloads two JDKs. Proper stubs are TBD with Gradle.
  @TargetVersions("6.7+")
  fun `simple toolchain support`() {
    VfsRootAccess.allowRootAccess(testRootDisposable, SystemProperties.getUserHome() + "/.gradle/jdks")
    allowAccessToDirsIfExists()
    importProject {
      withJavaPlugin()
      addPrefix("""
        java.toolchain.languageVersion.set(JavaLanguageVersion.of(15))
        compileJava {
            javaCompiler = javaToolchains.compilerFor {
                languageVersion = JavaLanguageVersion.of(13)
            }
        }
      """.trimIndent())
    }
    assertModules("project", "project.main", "project.test")
    assertModuleLanguageLevel("project.main", LanguageLevel.JDK_13)
    assertModuleLanguageLevel("project.test", LanguageLevel.JDK_15)
    assertModuleSdk("project.main", JavaSdkVersion.JDK_13)
    assertModuleSdk("project.test", JavaSdkVersion.JDK_15)
  }


  @Test
  @Ignore("The test is too slow: it downloads two JDKs. Proper stubs are TBD with Gradle.")
  @TargetVersions("6.7+")
  fun `update toolchain in build script should update it in IDEA`() {
    VfsRootAccess.allowRootAccess(testRootDisposable, SystemProperties.getUserHome() + "/.gradle/jdks")
    allowAccessToDirsIfExists()
    importProject {
      withJavaPlugin()
      addPrefix("""
        java.toolchain.languageVersion.set(JavaLanguageVersion.of(13))
      """.trimIndent())
    }
    assertModules("project", "project.main", "project.test")
    assertModuleLanguageLevel("project.main", LanguageLevel.JDK_13)
    assertModuleSdk("project.main", JavaSdkVersion.JDK_13)

    importProject {
      withJavaPlugin()
      addPrefix("""
        java.toolchain.languageVersion.set(JavaLanguageVersion.of(15))
      """.trimIndent())
    }

    assertModules("project", "project.main", "project.test")
    assertModuleLanguageLevel("project.main", LanguageLevel.JDK_15)
    assertModuleSdk("project.main", JavaSdkVersion.JDK_15)
  }

}