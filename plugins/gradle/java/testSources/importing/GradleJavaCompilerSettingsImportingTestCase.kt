// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.isJavaConventionsBlockSupported
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
        if (isJavaConventionsBlockSupported(gradleVersion)) {
          callIfNotEmpty("java") {
            assignIfNotNull("sourceCompatibility", projectSourceCompatibility)
            assignIfNotNull("targetCompatibility", projectTargetCompatibility)
          }
        }
        else {
          assignIfNotNull("sourceCompatibility", projectSourceCompatibility)
          assignIfNotNull("targetCompatibility", projectTargetCompatibility)
        }
        call("compileJava") {
          assignIfNotNull("sourceCompatibility", mainSourceCompatibility)
          assignIfNotNull("targetCompatibility", mainTargetCompatibility)
          if (mainSourceCompatibilityEnablePreview) {
            call("options.compilerArgs.add", "--enable-preview")
          }
        }
        call("compileTestJava") {
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