// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.settingsScript

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.name

@ApiStatus.NonExtendable
abstract class AbstractGradleSettingScriptBuilder<Self : AbstractGradleSettingScriptBuilder<Self>>
  : AbstractGradleSettingScriptBuilderCore<Self>(),
    GradleSettingScriptBuilder<Self> {

  override fun include(relativePath: Path): Self = apply {
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
}