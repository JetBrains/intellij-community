// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gradle.frameworkSupport.script.GroovyScriptBuilder.Companion.groovy
import org.jetbrains.plugins.gradle.testFramework.util.buildscript
import org.jetbrains.plugins.gradle.tooling.VersionMatcherRule
import org.jetbrains.plugins.groovy.lang.parser.closureParameter
import org.junit.runners.Parameterized

abstract class GradleJavaCompilerSettingsImportingTestCase : GradleJavaImportingTestCase() {

  var isNotSupportedCompilerArgumentProviders: Boolean = false
    private set
  var isNotSupportedJava14: Boolean = false
    private set

  override fun setUp() {
    super.setUp()
    isNotSupportedJava14 = isGradleOlderThan("6.3")
    isNotSupportedCompilerArgumentProviders = isGradleOlderThan("4.5")
  }

  fun createGradleSettingsFile(vararg moduleNames: String) {
    createSettingsFile(
      groovy {
        assign("rootProject.name", "project")
        call("include", *moduleNames)
      }
    )
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
    val file = createProjectSubFile("$relativePath/build.gradle", buildscript {
      withJavaPlugin()
      withPrefix {
        assignIfNotNull("sourceCompatibility", projectSourceCompatibility)
        assignIfNotNull("targetCompatibility", projectTargetCompatibility)
        call("compileJava") {
          assignIfNotNull("sourceCompatibility", mainSourceCompatibility)
          assignIfNotNull("targetCompatibility", mainTargetCompatibility)
          if (mainSourceCompatibilityEnablePreview) {
            if (isNotSupportedCompilerArgumentProviders) {
              call("options.compilerArgs.add", "--enable-preview")
            } else {
              code("options.compilerArgumentProviders.add({ ['--enable-preview'] } as CommandLineArgumentProvider)")
            }
          }
        }
        call("compileTestJava") {
          assignIfNotNull("sourceCompatibility", testSourceCompatibility)
          assignIfNotNull("targetCompatibility", testTargetCompatibility)
          if (testSourceCompatibilityEnablePreview) {
            if (isNotSupportedCompilerArgumentProviders) {
              call("options.compilerArgs.add", "--enable-preview")
            } else {
              code("options.compilerArgumentProviders.add({ ['--enable-preview'] } as CommandLineArgumentProvider)")
            }
          }
        }
      }
    })
    return file
  }

  companion object {
    @Parameterized.Parameters(name = "with Gradle-{0}")
    @JvmStatic
    fun tests() = arrayListOf(*VersionMatcherRule.SUPPORTED_GRADLE_VERSIONS, arrayOf("6.3"))
  }
}