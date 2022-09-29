// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleFileTestUtil")

package org.jetbrains.plugins.gradle.testFramework.util

import com.intellij.openapi.externalSystem.util.findOrCreateFile
import com.intellij.openapi.externalSystem.util.runWriteActionAndGet
import com.intellij.openapi.externalSystem.util.text
import com.intellij.openapi.vfs.VirtualFile
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.importing.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.testFramework.configuration.TestFilesConfiguration


fun GradleImportingTestCase.importProject(configure: TestGradleBuildScriptBuilder.() -> Unit) =
  importProject(script(configure))

fun settings(configure: GradleSettingScriptBuilder.() -> Unit) =
  GradleSettingScriptBuilder().apply(configure).generate()

fun buildscript(gradleVersion: GradleVersion, configure: TestGradleBuildScriptBuilder.() -> Unit): String =
  TestGradleBuildScriptBuilder(gradleVersion).apply(configure).generate()

fun GradleImportingTestCase.buildscript(configure: TestGradleBuildScriptBuilder.() -> Unit): String = script(configure)

fun GradleImportingTestCase.createSettingsFile(configure: GradleSettingScriptBuilder.() -> Unit) = createSettingsFile(".", configure)
fun GradleImportingTestCase.createSettingsFile(relativeModulePath: String, configure: GradleSettingScriptBuilder.() -> Unit) =
  projectRoot.createSettingsFile(relativeModulePath, configure)

fun GradleImportingTestCase.createBuildFile(configure: TestGradleBuildScriptBuilder.() -> Unit) = createBuildFile(".", configure)
fun GradleImportingTestCase.createBuildFile(relativeModulePath: String, configure: TestGradleBuildScriptBuilder.() -> Unit) =
  projectRoot.createBuildFile(relativeModulePath, buildscript(configure))

fun VirtualFile.createSettingsFile(configure: GradleSettingScriptBuilder.() -> Unit) = createSettingsFile(".", configure)
fun VirtualFile.createSettingsFile(relativeModulePath: String, configure: GradleSettingScriptBuilder.() -> Unit) =
  createSettingsFile(relativeModulePath, settings(configure))

fun VirtualFile.createSettingsFile(content: String) = createSettingsFile(".", content)
fun VirtualFile.createSettingsFile(relativeModulePath: String, content: String) =
  runWriteActionAndGet {
    findOrCreateFile("$relativeModulePath/settings.gradle")
      .also { it.text = content }
  }

fun VirtualFile.createBuildFile(gradleVersion: GradleVersion, configure: TestGradleBuildScriptBuilder.() -> Unit) =
  createBuildFile(".", gradleVersion, configure)

fun VirtualFile.createBuildFile(
  relativeModulePath: String,
  gradleVersion: GradleVersion,
  configure: TestGradleBuildScriptBuilder.() -> Unit
) = createBuildFile(relativeModulePath, buildscript(gradleVersion, configure))

fun VirtualFile.createBuildFile(content: String) = createBuildFile(".", content)
fun VirtualFile.createBuildFile(relativeModulePath: String, content: String) =
  runWriteActionAndGet {
    findOrCreateFile("$relativeModulePath/build.gradle")
      .also { it.text = content }
  }

fun TestFilesConfiguration.withSettingsFile(configure: GradleSettingScriptBuilder.() -> Unit) = withSettingsFile(".", configure)
fun TestFilesConfiguration.withSettingsFile(relativeModulePath: String, configure: GradleSettingScriptBuilder.() -> Unit) =
  withSettingsFile(relativeModulePath, settings(configure))

fun TestFilesConfiguration.withSettingsFile(content: String) = withSettingsFile(".", content)
fun TestFilesConfiguration.withSettingsFile(relativeModulePath: String, content: String) =
  withFile("$relativeModulePath/settings.gradle", content)

fun TestFilesConfiguration.withBuildFile(gradleVersion: GradleVersion, configure: TestGradleBuildScriptBuilder.() -> Unit) =
  withBuildFile(".", gradleVersion, configure)

fun TestFilesConfiguration.withBuildFile(
  relativeModulePath: String,
  gradleVersion: GradleVersion,
  configure: TestGradleBuildScriptBuilder.() -> Unit
) = withBuildFile(relativeModulePath, buildscript(gradleVersion, configure))

fun TestFilesConfiguration.withBuildFile(content: String) = withBuildFile(".", content)
fun TestFilesConfiguration.withBuildFile(relativeModulePath: String, content: String) =
  withFile("$relativeModulePath/build.gradle", content)
