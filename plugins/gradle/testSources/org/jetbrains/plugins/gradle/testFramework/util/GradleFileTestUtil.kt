// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleFileTestUtil")

package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.openapi.externalSystem.util.*
import com.intellij.openapi.vfs.VirtualFile
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.importing.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.importing.GradleSettingScriptBuilderImpl
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.testFramework.configuration.TestFilesConfiguration

fun GradleImportingTestCase.importProject(
  configure: TestGradleBuildScriptBuilder.() -> Unit
) = importProject(script(configure))

fun buildSettings(
  useKotlinDsl: Boolean = false,
  configure: GradleSettingScriptBuilder.() -> Unit
) = GradleSettingScriptBuilderImpl()
  .apply(configure)
  .generate(useKotlinDsl)

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
  findOrCreateFile(getSettingsFilePath(relativeModulePath, useKotlinDsl))
    .also { it.text = content }
}

fun VirtualFile.createBuildFile(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false,
  content: String
) = runWriteActionAndGet {
  findOrCreateFile(getBuildFilePath(relativeModulePath, useKotlinDsl))
    .also { it.text = content }
}

fun VirtualFile.getSettingsFile(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false
) = runReadAction {
  getFile(getSettingsFilePath(relativeModulePath, useKotlinDsl))
}

fun VirtualFile.getBuildFile(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false
) = runReadAction {
  getFile(getBuildFilePath(relativeModulePath, useKotlinDsl))
}

fun TestFilesConfiguration.withSettingsFile(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false,
  configure: GradleSettingScriptBuilder.() -> Unit
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

private fun getSettingsFilePath(relativeModulePath: String, useKotlinDsl: Boolean) =
  when (useKotlinDsl) {
    true -> "$relativeModulePath/settings.gradle.kts"
    else -> "$relativeModulePath/settings.gradle"
  }

private fun getBuildFilePath(relativeModulePath: String, useKotlinDsl: Boolean) =
  when (useKotlinDsl) {
    true -> "$relativeModulePath/build.gradle.kts"
    else -> "$relativeModulePath/build.gradle"
  }
