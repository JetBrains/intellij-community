// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.util

import com.intellij.gradle.testFramework.importing.GradleImportingTestCase
import com.intellij.gradle.testFramework.importing.TestGradleBuildScriptBuilder
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.openapi.vfs.writeText
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl.Companion.buildScriptName
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl.Companion.settingsScriptName
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder


fun GradleImportingTestCase.importProject(
  configure: TestGradleBuildScriptBuilder.() -> Unit,
) {
  importProject(script(configure))
}

fun GradleImportingTestCase.createSettingsFile(
  relativeModulePath: String = ".",
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
  configure: GradleSettingScriptBuilder<*>.() -> Unit,
): VirtualFile {
  return runWriteActionAndWait {
    myProjectRoot.findOrCreateDirectory(relativeModulePath)
      .findOrCreateFile(gradleDsl.settingsScriptName).apply {
        writeText(settingsScript(configure))
      }
  }
}

fun GradleImportingTestCase.createBuildFile(
  relativeModulePath: String = ".",
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
  configure: TestGradleBuildScriptBuilder.() -> Unit,
): VirtualFile {
  return runWriteActionAndWait {
    myProjectRoot.findOrCreateDirectory(relativeModulePath)
      .findOrCreateFile(gradleDsl.buildScriptName).apply {
        writeText(script(configure))
      }
  }
}

fun GradleImportingTestCase.createGradleWrapper(
  relativeModulePath: String = ".",
) {
  runWriteActionAndWait {
    myProjectRoot.findOrCreateDirectory(relativeModulePath)
      .createGradleWrapper(currentGradleVersion)
  }
}