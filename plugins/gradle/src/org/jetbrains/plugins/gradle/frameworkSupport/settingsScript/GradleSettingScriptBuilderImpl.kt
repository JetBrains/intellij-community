// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.settingsScript

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.apply as applyKt

internal class GradleSettingScriptBuilderImpl(
  gradleVersion: GradleVersion,
  gradleDsl: GradleDsl,
) : AbstractGradleSettingScriptBuilderCore<GradleSettingScriptBuilderImpl>(gradleVersion, gradleDsl),
    GradleSettingScriptBuilder<GradleSettingScriptBuilderImpl> {

  private val foojayPluginVersion = getFoojayPluginVersion()

  override fun apply(action: GradleSettingScriptBuilderImpl.() -> Unit) = applyKt(action)

  override fun include(relativePath: Path) = apply {
    val projectName = relativePath
      .dropWhile { it.name == ".." }
      .joinToString(":") { it.name }
    when {
      relativePath.startsWith("..") && relativePath.nameCount == 2 -> {
        includeFlat(projectName)
      }
      relativePath.startsWith("..") -> {
        include(projectName)
        setProjectDir(":$projectName", relativePath.toString())
      }
      else -> {
        include(projectName)
      }
    }
  }

  override fun withFoojayPlugin() = apply {
    assert(isFoojayPluginSupported(gradleVersion))
    withPlugin("org.gradle.toolchains.foojay-resolver-convention", foojayPluginVersion)
  }
}