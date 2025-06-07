// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleFileTestUtil")
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.openapi.util.io.findOrCreateDirectory
import com.intellij.openapi.util.io.findOrCreateFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.openapi.vfs.writeText
import com.intellij.testFramework.utils.vfs.getDirectory
import com.intellij.testFramework.utils.vfs.getFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder.Companion.buildScript
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder.Companion.getBuildScriptName
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder.Companion.getSettingsScriptName
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder.Companion.settingsScript
import org.jetbrains.plugins.gradle.testFramework.configuration.TestFilesConfiguration
import java.nio.file.Path
import kotlin.io.path.writeText

@RequiresWriteLock
fun VirtualFile.createSettingsFile(
  gradleVersion: GradleVersion,
  relativeModulePath: String = ".",
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
  configure: GradleSettingScriptBuilder<*>.() -> Unit,
): VirtualFile {
  val content = settingsScript(gradleVersion, gradleDsl, configure)
  return createSettingsFile(relativeModulePath, gradleDsl, content)
}

@RequiresWriteLock
fun VirtualFile.createBuildFile(
  gradleVersion: GradleVersion,
  relativeModulePath: String = ".",
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
  configure: GradleBuildScriptBuilder<*>.() -> Unit,
): VirtualFile {
  val content = buildScript(gradleVersion, gradleDsl, configure)
  return createBuildFile(relativeModulePath, gradleDsl, content)
}

@RequiresWriteLock
fun VirtualFile.createSettingsFile(
  relativeModulePath: String = ".",
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
  content: String,
): VirtualFile {
  return findOrCreateDirectory(relativeModulePath)
    .findOrCreateFile(getSettingsScriptName(gradleDsl))
    .also { it.writeText(content) }
}

@RequiresWriteLock
fun VirtualFile.createBuildFile(
  relativeModulePath: String = ".",
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
  content: String,
): VirtualFile {
  return findOrCreateDirectory(relativeModulePath)
    .findOrCreateFile(getBuildScriptName(gradleDsl))
    .also { it.writeText(content) }
}

@RequiresReadLock
fun VirtualFile.getSettingsFile(
  relativeModulePath: String = ".",
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
): VirtualFile {
  return getDirectory(relativeModulePath)
    .getFile(getSettingsScriptName(gradleDsl))
}

@RequiresReadLock
fun VirtualFile.getBuildFile(
  relativeModulePath: String = ".",
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
): VirtualFile {
  return getDirectory(relativeModulePath)
    .getFile(getBuildScriptName(gradleDsl))
}

fun TestFilesConfiguration.withSettingsFile(
  gradleVersion: GradleVersion,
  relativeModulePath: String = ".",
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
  configure: GradleSettingScriptBuilder<*>.() -> Unit,
) {
  val content = settingsScript(gradleVersion, gradleDsl, configure)
  withSettingsFile(relativeModulePath, gradleDsl, content)
}

fun TestFilesConfiguration.withBuildFile(
  gradleVersion: GradleVersion,
  relativeModulePath: String = ".",
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
  configure: GradleBuildScriptBuilder<*>.() -> Unit,
) {
  val content = buildScript(gradleVersion, gradleDsl, configure)
  withBuildFile(relativeModulePath, gradleDsl, content)
}

fun TestFilesConfiguration.withSettingsFile(
  relativeModulePath: String = ".",
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
  content: String,
) {
  withFile(relativeModulePath + "/" + getSettingsScriptName(gradleDsl), content)
}

fun TestFilesConfiguration.withBuildFile(
  relativeModulePath: String = ".",
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
  content: String,
) {
  withFile(relativeModulePath + "/" + getBuildScriptName(gradleDsl), content)
}

fun Path.createSettingsFile(
  gradleVersion: GradleVersion,
  relativeModulePath: String = ".",
  gradleDsl: GradleDsl = GradleDsl.KOTLIN,
  configure: GradleSettingScriptBuilder<*>.() -> Unit,
): Path {
  return findOrCreateDirectory(relativeModulePath)
    .findOrCreateFile(getSettingsScriptName(gradleDsl))
    .also { it.writeText(settingsScript(gradleVersion, gradleDsl, configure)) }
}

fun Path.createBuildFile(
  gradleVersion: GradleVersion,
  relativeModulePath: String = ".",
  gradleDsl: GradleDsl = GradleDsl.KOTLIN,
  configure: GradleBuildScriptBuilder<*>.() -> Unit,
): Path {
  return findOrCreateDirectory(relativeModulePath)
    .findOrCreateFile(getBuildScriptName(gradleDsl))
    .also { it.writeText(buildScript(gradleVersion, gradleDsl, configure)) }
}
