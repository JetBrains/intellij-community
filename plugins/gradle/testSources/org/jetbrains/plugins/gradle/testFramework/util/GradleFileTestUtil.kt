// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleFileTestUtil")
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.openapi.externalSystem.util.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.testFramework.utils.vfs.getFile
import com.intellij.openapi.vfs.writeText
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.testFramework.configuration.TestFilesConfiguration

fun GradleImportingTestCase.importProject(
  configure: TestGradleBuildScriptBuilder.() -> Unit
) = importProject(script(configure))

fun buildSettings(
  useKotlinDsl: Boolean = false,
  configure: GradleSettingScriptBuilder<*>.() -> Unit
) = GradleSettingScriptBuilder.create(useKotlinDsl)
  .apply(configure)
  .generate()

fun buildscript(
  gradleVersion: GradleVersion,
  configure: TestGradleBuildScriptBuilder.() -> Unit
) = TestGradleBuildScriptBuilder(gradleVersion)
  .apply(configure)
  .generate()

fun GradleImportingTestCase.buildscript(
  configure: TestGradleBuildScriptBuilder.() -> Unit
) = script(configure)

fun GradleImportingTestCase.createSettingsFile(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false,
  configure: GradleSettingScriptBuilder.() -> Unit
) = projectRoot.createSettingsFile(
  relativeModulePath = relativeModulePath,
  useKotlinDsl = useKotlinDsl,
  configure = configure
)

fun GradleImportingTestCase.createBuildFile(
  relativeModulePath: String = ".",
  configure: TestGradleBuildScriptBuilder.() -> Unit
) = projectRoot.createBuildFile(
  relativeModulePath = relativeModulePath,
  useKotlinDsl = false,
  content = buildscript(configure)
)

fun VirtualFile.createSettingsFile(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false,
  configure: GradleSettingScriptBuilder.() -> Unit
) = createSettingsFile(
  relativeModulePath = relativeModulePath,
  useKotlinDsl = useKotlinDsl,
  content = buildSettings(useKotlinDsl, configure)
)

fun VirtualFile.createBuildFile(
  gradleVersion: GradleVersion,
  relativeModulePath: String = ".",
  configure: TestGradleBuildScriptBuilder.() -> Unit
) = createBuildFile(
  relativeModulePath = relativeModulePath,
  useKotlinDsl = false,
  content = buildscript(gradleVersion, configure)
)

fun VirtualFile.createSettingsFile(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false,
  content: String
) = runWriteActionAndGet {
  val path = getSettingsFilePath(relativeModulePath, useKotlinDsl)
  val file = findOrCreateFile(path)
  file.writeText(content)
}

fun VirtualFile.createBuildFile(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false,
  content: String
) = runWriteActionAndGet {
  val path = getBuildFilePath(relativeModulePath, useKotlinDsl)
  val file = findOrCreateFile(path)
  file.writeText(content)
}

fun VirtualFile.getSettingsFile(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false
) = runReadAction {
  val path = getSettingsFilePath(relativeModulePath, useKotlinDsl)
  getFile(path)
}

fun VirtualFile.getBuildFile(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false
) = runReadAction {
  val path = getBuildFilePath(relativeModulePath, useKotlinDsl)
  getFile(path)
}

fun TestFilesConfiguration.withSettingsFile(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false,
  configure: GradleSettingScriptBuilder<*>.() -> Unit
) = withSettingsFile(
  relativeModulePath = relativeModulePath,
  useKotlinDsl = useKotlinDsl,
  content = buildSettings(useKotlinDsl, configure)
)

fun TestFilesConfiguration.withBuildFile(
  gradleVersion: GradleVersion,
  relativeModulePath: String = ".",
  configure: TestGradleBuildScriptBuilder.() -> Unit
) = withBuildFile(
  relativeModulePath = relativeModulePath,
  useKotlinDsl = false,
  content = buildscript(gradleVersion, configure)
)

fun TestFilesConfiguration.withSettingsFile(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false,
  content: String
) = withFile(getSettingsFilePath(relativeModulePath, useKotlinDsl), content)

fun TestFilesConfiguration.withBuildFile(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false,
  content: String
) = withFile(getBuildFilePath(relativeModulePath, useKotlinDsl), content)

private fun getSettingsFilePath(relativeModulePath: String, useKotlinDsl: Boolean): String {
  return when (useKotlinDsl) {
    true -> "$relativeModulePath/settings.gradle.kts"
    else -> "$relativeModulePath/settings.gradle"
  }
}

private fun getBuildFilePath(relativeModulePath: String, useKotlinDsl: Boolean): String {
  return when (useKotlinDsl) {
    true -> "$relativeModulePath/build.gradle.kts"
    else -> "$relativeModulePath/build.gradle"
  }
}
