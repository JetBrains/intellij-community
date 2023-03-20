// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.nastradamus.model

import com.fasterxml.jackson.annotation.JsonProperty

data class ChangeEntity(
  @JsonProperty("file_path")
  val filePath: String, // E.g: "fleet/code/frontend/src/fleet/frontend/code/completion/CompletionReports.kt"

  @JsonProperty("relative_file")
  val relativeFile: String, // E.g: "fleet/code/frontend/src/fleet/frontend/code/completion/CompletionReports.kt"

  @JsonProperty("before_revision")
  val beforeRevision: String, // E.g: "783412a5dd2d5c5b8de890cb64320a101aebdaf2"

  @JsonProperty("after_revision")
  val afterRevision: String, // E.g: "1fed3565ed9ea6821c3be127b6fc2f35a507b7f1"

  @JsonProperty("change_type")
  val changeType: String, // E.g: "edited"

  val comment: String, // comment from VCS

  @JsonProperty("user_name")
  val userName: String,

  val date: String // E.g: 20221121T110802+0000
)
