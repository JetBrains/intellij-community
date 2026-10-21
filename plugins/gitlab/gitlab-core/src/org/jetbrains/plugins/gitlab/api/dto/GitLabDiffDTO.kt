// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import org.jetbrains.plugins.gitlab.api.SinceGitLab

data class GitLabDiffDTO(
  val aMode: String?,
  val bMode: String?,

  val diff: String,

  val deletedFile: Boolean,
  val newFile: Boolean,
  val renamedFile: Boolean,

  val newPath: String?,
  val oldPath: String?,

  /**
   * True when the server left [diff] out because the file is over the lower diff limit.
   * The server keeps the diff, so the IDE can build the same diff from the local repository.
   */
  @SinceGitLab("18.4") val collapsed: Boolean? = null,

  /**
   * True when the server left [diff] out because the file is over the higher diff limit.
   * The server keeps no diff, so a note on the file has nothing to anchor to.
   */
  @SinceGitLab("18.4") val tooLarge: Boolean? = null,
)

val GitLabDiffDTO.filePath: String
  get() = newPath.takeIf { !deletedFile }
          ?: oldPath.takeIf { !newFile }
          ?: error("Invalid diff without a file path")