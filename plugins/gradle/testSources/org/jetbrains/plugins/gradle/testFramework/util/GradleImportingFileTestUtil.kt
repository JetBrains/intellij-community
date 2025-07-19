// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.openapi.externalSystem.util.runWriteActionAndGet
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.openapi.vfs.writeText
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder.Companion.getBuildScriptName
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder.Companion.getSettingsScriptName
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder


fun GradleImportingTestCase.importProject(
  configure: TestGradleBuildScriptBuilder.() -> Unit,
) {
  importProject(script(configure))
}

fun GradleImportingTestCase.createSettingsFile(
  relativeModulePath: String = ".",
  configure: GradleSettingScriptBuilder<*>.() -> Unit,
): VirtualFile {
  return runWriteActionAndGet {
    projectRoot.findOrCreateDirectory(relativeModulePath)
      .findOrCreateFile(getSettingsScriptName(GradleDsl.GROOVY)).apply {
        writeText(settingsScript(configure))
      }
  }
}

fun GradleImportingTestCase.createBuildFile(
  relativeModulePath: String = ".",
  configure: TestGradleBuildScriptBuilder.() -> Unit,
): VirtualFile {
  return runWriteActionAndGet {
    projectRoot.findOrCreateDirectory(relativeModulePath)
      .findOrCreateFile(getBuildScriptName(GradleDsl.GROOVY)).apply {
        writeText(script(configure))
      }
  }
}

fun GradleImportingTestCase.createGradleWrapper(
  relativeModulePath: String = ".",
) {
  runWriteActionAndGet {
    projectRoot.findOrCreateDirectory(relativeModulePath)
      .createGradleWrapper(currentGradleVersion)
  }
}