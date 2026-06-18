// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.gradle

import java.nio.file.Path

/**
 * Data class corresponding to a Gradle module, containing the common Compose resources settings and source-set specific information.
 */
internal data class GradleComposeResources(
  val moduleName: String,
  val directoriesBySourceSetName: Map<String, GradleComposeResourcesDir>,
  val isPublicResClass: Boolean,
  val nameOfResClass: String,
  val packageOfResClass: String,
)

/**
 * Data class corresponding to a Gradle source set.
 */
internal data class GradleComposeResourcesDir(
  val moduleName: String,
  val sourceSetName: String,
  val directoryPath: Path,
  val projectGroupName: String,
  val isCustom: Boolean = false,
)
