// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.openapi.externalSystem.util.runWriteActionAndGet
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder


fun GradleImportingTestCase.importProject(
  configure: TestGradleBuildScriptBuilder.() -> Unit
) = importProject(script(configure))

fun GradleImportingTestCase.createSettingsFile(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false,
  configure: GradleSettingScriptBuilder<*>.() -> Unit
) = runWriteActionAndGet {
  projectRoot.createSettingsFile(
    relativeModulePath = relativeModulePath,
    useKotlinDsl = useKotlinDsl,
    content = settingsScript(configure)
  )
}

fun GradleImportingTestCase.createBuildFile(
  relativeModulePath: String = ".",
  configure: TestGradleBuildScriptBuilder.() -> Unit
) = runWriteActionAndGet {
  projectRoot.createBuildFile(
    relativeModulePath = relativeModulePath,
    useKotlinDsl = false,
    content = script(configure)
  )
}