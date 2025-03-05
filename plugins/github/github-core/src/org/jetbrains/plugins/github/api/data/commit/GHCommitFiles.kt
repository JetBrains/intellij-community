// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api.data.commit

data class GHCommitFiles(
  val files: List<GHCommitFile>
)

data class GHCommitFile(
  val previousFilename: String?,
  val filename: String,
  val status: Status,
  val patch: String? // null for binary files
) {
  @Suppress("EnumEntryName")
  enum class Status {
    added, removed, modified, renamed, copied, changed, unchanged
  }
}