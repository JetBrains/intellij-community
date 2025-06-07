// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.openapi.externalSystem.util.runWriteActionAndGet
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
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
    projectRoot.createSettingsFile(relativeModulePath, GradleDsl.GROOVY, settingsScript(configure))
  }
}

fun GradleImportingTestCase.createBuildFile(
  relativeModulePath: String = ".",
  configure: TestGradleBuildScriptBuilder.() -> Unit,
): VirtualFile {
  return runWriteActionAndGet {
    projectRoot.createBuildFile(relativeModulePath, GradleDsl.GROOVY, script(configure))
  }
}