// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * The unversioned files for the Local Changes tree.
 */
@Serializable
@ApiStatus.Internal
sealed interface UnversionedFilesDto {
  /** The tree shows each file in [files]. */
  @Serializable
  data class Files(val files: List<FilePathDto>) : UnversionedFilesDto

  /** The list is too large for the tree, so the client shows only the counts. */
  @Serializable
  data class Counts(val fileCount: Int, val directoryCount: Int) : UnversionedFilesDto
}