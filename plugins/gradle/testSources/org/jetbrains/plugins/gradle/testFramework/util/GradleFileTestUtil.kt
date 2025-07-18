// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleFileTestUtil")
@file:Suppress("MemberVisibilityCanBePrivate", "unused")
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.openapi.util.io.findOrCreateFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.openapi.vfs.writeText
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
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
  configure: GradleSettingScriptBuilder<*>.() -> Unit,
): VirtualFile {
  val content = settingsScript(gradleVersion, gradleDsl, configure)
  return createSettingsFile(gradleDsl, content)
}

@RequiresWriteLock
fun VirtualFile.createBuildFile(
  gradleVersion: GradleVersion,
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
  configure: GradleBuildScriptBuilder<*>.() -> Unit,
): VirtualFile {
  val content = buildScript(gradleVersion, gradleDsl, configure)
  return createBuildFile(gradleDsl, content)
}

@RequiresWriteLock
fun VirtualFile.createSettingsFile(
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
  content: String,
): VirtualFile {
  return findOrCreateFile(getSettingsScriptName(gradleDsl)).apply {
    writeText(content)
  }
}

@RequiresWriteLock
fun VirtualFile.createBuildFile(
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
  content: String,
): VirtualFile {
  return findOrCreateFile(getBuildScriptName(gradleDsl)).apply {
    writeText(content)
  }
}

@RequiresReadLock
fun VirtualFile.getSettingsFile(
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
): VirtualFile {
  return getFile(getSettingsScriptName(gradleDsl))
}

@RequiresReadLock
fun VirtualFile.getBuildFile(
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
): VirtualFile {
  return getFile(getBuildScriptName(gradleDsl))
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
  gradleDsl: GradleDsl = GradleDsl.KOTLIN,
  configure: GradleSettingScriptBuilder<*>.() -> Unit,
): Path {
  return findOrCreateFile(getSettingsScriptName(gradleDsl)).apply {
    writeText(settingsScript(gradleVersion, gradleDsl, configure))
  }
}

fun Path.createBuildFile(
  gradleVersion: GradleVersion,
  gradleDsl: GradleDsl = GradleDsl.KOTLIN,
  configure: GradleBuildScriptBuilder<*>.() -> Unit,
): Path {
  return findOrCreateFile(getBuildScriptName(gradleDsl)).apply {
    writeText(buildScript(gradleVersion, gradleDsl, configure))
  }
}
