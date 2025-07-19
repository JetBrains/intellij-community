// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleFileTestUtil")
package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.openapi.util.io.findOrCreateFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findOrCreateDirectory
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.openapi.vfs.writeText
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder.Companion.buildScript
import org.jetbrains.plugins.gradle.frameworkSupport.buildscript.GradleBuildScriptBuilder.Companion.getBuildScriptName
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder.Companion.getSettingsScriptName
import org.jetbrains.plugins.gradle.frameworkSupport.settingsScript.GradleSettingScriptBuilder.Companion.settingsScript
import org.jetbrains.plugins.gradle.service.project.wizard.util.generateGradleWrapper
import org.jetbrains.plugins.gradle.testFramework.configuration.TestFilesConfiguration
import java.nio.file.Path
import kotlin.io.path.writeText

@RequiresWriteLock
fun VirtualFile.createSettingsFile(
  gradleVersion: GradleVersion,
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
  configure: GradleSettingScriptBuilder<*>.() -> Unit = {},
): VirtualFile {
  return findOrCreateFile(getSettingsScriptName(gradleDsl)).apply {
    writeText(settingsScript(gradleVersion, gradleDsl, configure))
  }
}

@RequiresWriteLock
fun VirtualFile.createBuildFile(
  gradleVersion: GradleVersion,
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
  configure: GradleBuildScriptBuilder<*>.() -> Unit = {},
): VirtualFile {
  return findOrCreateFile(getBuildScriptName(gradleDsl)).apply {
    writeText(buildScript(gradleVersion, gradleDsl, configure))
  }
}

@RequiresWriteLock
fun VirtualFile.createGradleWrapper(gradleVersion: GradleVersion) {
  generateGradleWrapper(this, gradleVersion)
}

fun TestFilesConfiguration.withSettingsFile(
  gradleVersion: GradleVersion,
  relativeModulePath: String = ".",
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
  configure: GradleSettingScriptBuilder<*>.() -> Unit = {},
) {
  withFile(
    relativePath = relativeModulePath + "/" + getSettingsScriptName(gradleDsl),
    content = settingsScript(gradleVersion, gradleDsl, configure)
  )
}

fun TestFilesConfiguration.withBuildFile(
  gradleVersion: GradleVersion,
  relativeModulePath: String = ".",
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
  configure: GradleBuildScriptBuilder<*>.() -> Unit = {},
) {
  withFile(
    relativePath = relativeModulePath + "/" + getBuildScriptName(gradleDsl),
    content = buildScript(gradleVersion, gradleDsl, configure)
  )
}

@Deprecated("Instead use the withBuildFile function with build script configurator")
fun TestFilesConfiguration.withBuildFile(
  relativeModulePath: String = ".",
  gradleDsl: GradleDsl = GradleDsl.GROOVY,
  content: String,
) {
  withFile(relativeModulePath + "/" + getBuildScriptName(gradleDsl), content)
}

fun TestFilesConfiguration.withGradleWrapper(
  gradleVersion: GradleVersion,
  relativeModulePath: String = ".",
) {
  withFiles {
    it.findOrCreateDirectory(relativeModulePath)
      .createGradleWrapper(gradleVersion)
  }
}

fun Path.createSettingsFile(
  gradleVersion: GradleVersion,
  gradleDsl: GradleDsl = GradleDsl.KOTLIN,
  configure: GradleSettingScriptBuilder<*>.() -> Unit = {},
): Path {
  return findOrCreateFile(getSettingsScriptName(gradleDsl)).apply {
    writeText(settingsScript(gradleVersion, gradleDsl, configure))
  }
}

fun Path.createBuildFile(
  gradleVersion: GradleVersion,
  gradleDsl: GradleDsl = GradleDsl.KOTLIN,
  configure: GradleBuildScriptBuilder<*>.() -> Unit = {},
): Path {
  return findOrCreateFile(getBuildScriptName(gradleDsl)).apply {
    writeText(buildScript(gradleVersion, gradleDsl, configure))
  }
}

fun Path.createGradleWrapper(gradleVersion: GradleVersion) {
  generateGradleWrapper(this, gradleVersion)
}
