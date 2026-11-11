// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Names the project that takes a regular file that comes from outside the IDE: from the command line or from the system file manager.
 *
 * The platform asks every provider before it selects a project on its own, also when no project is open.
 * A provider can open a project for the file. The caller opens the file in the returned project.
 */
@ApiStatus.Internal
interface ExternalFileTargetProjectProvider {
  /** Returns the project that takes [file], or `null` when this provider has no preference. */
  suspend fun getTargetProject(file: Path): Project?

  companion object {
    val EP_NAME: ExtensionPointName<ExternalFileTargetProjectProvider> =
      ExtensionPointName("com.intellij.externalFileTargetProjectProvider")

    /** Returns the first answer of [getTargetProject] among the registered providers. */
    suspend fun findTargetProject(file: Path): Project? {
      return EP_NAME.lazySequence().firstNotNullOfOrNull { it.getTargetProject(file) }
    }
  }
}
