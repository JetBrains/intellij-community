// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.api.dto

data class GitLabDiffPositionInput(
  val baseSha: String?, // Merge base of the branch the comment was made on.
  val startSha: String, // SHA of the branch being compared against.
  val oldLine: Int?, // SHA of the HEAD at the time the comment was made.
  val headSha: String, // Line on HEAD SHA that was changed.
  val newLine: Int?, // Line on start SHA that was changed.
  val paths: DiffPathsInput // The paths of the file that was changed. Both of the properties of this input are optional, but at least one of them is required.
)

data class DiffPathsInput(
  val newPath: String?,
  val oldPath: String?
)