// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.dto

data class GitLabMergeRequestDiffDTO(
  val aMode: String,
  val bMode: String,
  val diff: String,
  val deletedFile: Boolean,
  val newFile: Boolean,
  val newPath: String,
  val oldPath: String,
  val renamedFile: Boolean
)