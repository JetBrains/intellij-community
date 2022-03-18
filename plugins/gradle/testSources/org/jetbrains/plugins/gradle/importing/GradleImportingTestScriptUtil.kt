// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GradleImportingTestScriptUtil")

package org.jetbrains.plugins.gradle.importing

import com.intellij.openapi.externalSystem.test.ExternalSystemTestCase
import org.jetbrains.plugins.gradle.importing.GradleSettingScriptBuilder.Companion.settingsScript
import java.util.function.Consumer

fun GradleImportingTestCase.buildscript(configure: Consumer<TestGradleBuildScriptBuilder>) =
  buildscript(configure::accept)

fun GradleImportingTestCase.buildscript(configure: TestGradleBuildScriptBuilder.() -> Unit) =
  createBuildScriptBuilder().apply(configure).generate()

fun GradleImportingTestCase.createSettingsFile(configure: GradleSettingScriptBuilder.() -> Unit) {
  createSettingsFile(settingsScript("project", configure))
}

fun GradleImportingTestCase.createSettingsFile(relativeModulePath: String = ".", configure: GradleSettingScriptBuilder.() -> Unit) {
  val file = createProjectSubFile("$relativeModulePath/settings.gradle")
  val projectName = file.parent!!.name
  val script = settingsScript(projectName, configure)
  ExternalSystemTestCase.setFileContent(file, script, false)
}

fun GradleImportingTestCase.createBuildFile(relativeModulePath: String = ".", configure: TestGradleBuildScriptBuilder.() -> Unit) {
  createProjectSubFile("$relativeModulePath/build.gradle", buildscript(configure))
}

fun GradleImportingTestCase.importProject(configure: TestGradleBuildScriptBuilder.() -> Unit) {
  importProject(buildscript(configure))
}
