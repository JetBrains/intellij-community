// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginSystem.testFramework

import org.jetbrains.jps.model.module.JpsModule
import java.nio.file.Path

/**
 * Describes a resolved xi:include directive in plugin or content module XML descriptor
 */
data class ResolvedXIncludeDirective(
  val sourceModule: JpsModule,
  val targetModule: JpsModule,
  val sourceFile: Path,
  val targetFile: Path,
)
