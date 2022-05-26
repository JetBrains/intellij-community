// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleFileTestUtil")

package org.jetbrains.plugins.gradle.testFramework

import com.intellij.openapi.externalSystem.util.findOrCreateFile
import com.intellij.openapi.externalSystem.util.runWriteActionAndWait
import com.intellij.openapi.externalSystem.util.text
import com.intellij.openapi.vfs.VirtualFile
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.importing.GradleSettingScriptBuilder
import org.jetbrains.plugins.gradle.importing.GradleSettingScriptBuilder.Companion.settingsScript
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder
import org.jetbrains.plugins.gradle.importing.TestGradleBuildScriptBuilder.Companion.buildscript


@JvmOverloads
fun VirtualFile.createSettingsFile(
  relativeModulePath: String = ".",
  configure: GradleSettingScriptBuilder.() -> Unit
) = createSettingsFile(settingsScript(configure), relativeModulePath)

@JvmOverloads
fun VirtualFile.createSettingsFile(
  content: String,
  relativeModulePath: String = "."
) = runWriteActionAndWait {
  findOrCreateFile("$relativeModulePath/settings.gradle")
    .also { it.text = content }
}

@JvmOverloads
fun VirtualFile.createBuildFile(
  gradleVersion: GradleVersion,
  relativeModulePath: String = ".",
  configure: TestGradleBuildScriptBuilder.() -> Unit
) = createBuildFile(buildscript(gradleVersion, configure), relativeModulePath)

@JvmOverloads
fun VirtualFile.createBuildFile(
  content: String,
  relativeModulePath: String = "."
) = runWriteActionAndWait {
  findOrCreateFile("$relativeModulePath/build.gradle")
    .also { it.text = content }
}

@JvmOverloads
fun FileTestFixture.Builder.withSettingsFile(
  relativeModulePath: String = ".",
  configure: GradleSettingScriptBuilder.() -> Unit
) = withSettingsFile(settingsScript(configure), relativeModulePath)

@JvmOverloads
fun FileTestFixture.Builder.withSettingsFile(
  content: String,
  relativeModulePath: String = "."
) = withFile("$relativeModulePath/settings.gradle", content)

@JvmOverloads
fun FileTestFixture.Builder.withBuildFile(
  gradleVersion: GradleVersion,
  relativeModulePath: String = ".",
  configure: TestGradleBuildScriptBuilder.() -> Unit
) = withBuildFile(buildscript(gradleVersion, configure), relativeModulePath)

@JvmOverloads
fun FileTestFixture.Builder.withBuildFile(
  content: String,
  relativeModulePath: String = "."
) = withFile("$relativeModulePath/build.gradle", content)
