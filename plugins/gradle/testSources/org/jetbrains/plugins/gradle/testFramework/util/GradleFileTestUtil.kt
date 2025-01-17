// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleFileTestUtil")
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.openapi.vfs.writeText
import com.intellij.testFramework.utils.vfs.getFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.testFramework.configuration.TestFilesConfiguration

private fun settingsScript(
  gradleVersion: GradleVersion,
  useKotlinDsl: Boolean = false,
  configure: GradleSettingScriptBuilder<*>.() -> Unit
) = GradleSettingScriptBuilder.create(gradleVersion, useKotlinDsl)
  .apply(configure)
  .generate()

private fun buildScript(
  gradleVersion: GradleVersion,
  useKotlinDsl: Boolean = false,
  configure: GradleBuildScriptBuilder<*>.() -> Unit
) = GradleBuildScriptBuilder.create(gradleVersion, useKotlinDsl)
  .apply(configure)
  .generate()

@RequiresWriteLock
fun VirtualFile.createSettingsFile(
  gradleVersion: GradleVersion,
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false,
  configure: GradleSettingScriptBuilder<*>.() -> Unit
) = createSettingsFile(
  relativeModulePath = relativeModulePath,
  useKotlinDsl = useKotlinDsl,
  content = settingsScript(gradleVersion, useKotlinDsl, configure)
)

@RequiresWriteLock
fun VirtualFile.createBuildFile(
  gradleVersion: GradleVersion,
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false,
  configure: GradleBuildScriptBuilder<*>.() -> Unit
) = createBuildFile(
  relativeModulePath = relativeModulePath,
  useKotlinDsl = useKotlinDsl,
  content = buildScript(gradleVersion, useKotlinDsl, configure)
)

@RequiresWriteLock
fun VirtualFile.createSettingsFile(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false,
  content: String
): VirtualFile {
  val path = getSettingsFilePath(relativeModulePath, useKotlinDsl)
  val file = findOrCreateFile(path)
  file.writeText(content)
  return file
}

@RequiresWriteLock
fun VirtualFile.createBuildFile(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false,
  content: String
): VirtualFile {
  val path = getBuildFilePath(relativeModulePath, useKotlinDsl)
  val file = findOrCreateFile(path)
  file.writeText(content)
  return file
}

@RequiresReadLock
fun VirtualFile.getSettingsFile(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false
): VirtualFile {
  val path = getSettingsFilePath(relativeModulePath, useKotlinDsl)
  return getFile(path)
}

@RequiresReadLock
fun VirtualFile.getBuildFile(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false
): VirtualFile {
  val path = getBuildFilePath(relativeModulePath, useKotlinDsl)
  return getFile(path)
}

fun TestFilesConfiguration.withSettingsFile(
  gradleVersion: GradleVersion,
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false,
  configure: GradleSettingScriptBuilder<*>.() -> Unit
) = withSettingsFile(
  relativeModulePath = relativeModulePath,
  useKotlinDsl = useKotlinDsl,
  content = settingsScript(gradleVersion, useKotlinDsl, configure)
)

fun TestFilesConfiguration.withBuildFile(
  gradleVersion: GradleVersion,
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false,
  configure: GradleBuildScriptBuilder<*>.() -> Unit
) = withBuildFile(
  relativeModulePath = relativeModulePath,
  useKotlinDsl = useKotlinDsl,
  content = buildScript(gradleVersion, useKotlinDsl, configure)
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

fun getSettingsFilePath(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false
): String {
  return when (useKotlinDsl) {
    true -> "$relativeModulePath/settings.gradle.kts"
    else -> "$relativeModulePath/settings.gradle"
  }
}

fun getBuildFilePath(
  relativeModulePath: String = ".",
  useKotlinDsl: Boolean = false
): String {
  return when (useKotlinDsl) {
    true -> "$relativeModulePath/build.gradle.kts"
    else -> "$relativeModulePath/build.gradle"
  }
}
