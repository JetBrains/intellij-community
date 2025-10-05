// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil
import org.jetbrains.plugins.gradle.testFramework.util.createBuildFile
import org.jetbrains.plugins.gradle.testFramework.util.createSettingsFile

abstract class GradleJavaCompilerSettingsImportingTestCase : GradleJavaImportingTestCase() {

  fun createGradleSettingsFile(vararg moduleNames: String) {
    createSettingsFile {
      setProjectName("project")
      for (moduleName in moduleNames) {
        include(moduleName)
      }
    }
  }

  fun createJavaGradleSubProject(
    relativePath: String = ".",
    projectSourceCompatibility: String? = null,
    projectTargetCompatibility: String? = null,
    mainSourceCompatibility: String? = null,
    mainSourceCompatibilityEnablePreview: Boolean = false,
    mainTargetCompatibility: String? = null,
    testSourceCompatibility: String? = null,
    testSourceCompatibilityEnablePreview: Boolean = false,
    testTargetCompatibility: String? = null
  ): VirtualFile {
    createProjectSubDir("$relativePath/src/main/java")
    createProjectSubDir("$relativePath/src/test/java")
    return createBuildFile(relativePath) {
      withJavaPlugin()
      withPrefix {
        // Since Gradle 7.1, the source and target compatiblity can be defined by string value
        if (GradleVersionUtil.isGradleAtLeast(gradleVersion, "7.1")) {
          withJava {
            assignIfNotNull("sourceCompatibility", projectSourceCompatibility)
            assignIfNotNull("targetCompatibility", projectTargetCompatibility)
          }
        }
        else {
          assignIfNotNull("sourceCompatibility", projectSourceCompatibility)
          assignIfNotNull("targetCompatibility", projectTargetCompatibility)
        }
        compileJava {
          assignIfNotNull("sourceCompatibility", mainSourceCompatibility)
          assignIfNotNull("targetCompatibility", mainTargetCompatibility)
          if (mainSourceCompatibilityEnablePreview) {
            call("options.compilerArgs.add", "--enable-preview")
          }
        }
        compileTestJava {
          assignIfNotNull("sourceCompatibility", testSourceCompatibility)
          assignIfNotNull("targetCompatibility", testTargetCompatibility)
          if (testSourceCompatibilityEnablePreview) {
            call("options.compilerArgs.add", "--enable-preview")
          }
        }
      }
    }
  }
}