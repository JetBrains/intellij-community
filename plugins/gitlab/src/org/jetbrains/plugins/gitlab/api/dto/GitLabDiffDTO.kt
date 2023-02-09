// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

data class GitLabDiffDTO(
  val aMode: String?,
  val bMode: String?,

  val diff: String,

  val deletedFile: Boolean,
  val newFile: Boolean,
  val renamedFile: Boolean,

  val newPath: String?,
  val oldPath: String?
)