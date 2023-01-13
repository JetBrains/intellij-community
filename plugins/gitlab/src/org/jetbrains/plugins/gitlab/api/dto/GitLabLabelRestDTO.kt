// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

data class GitLabLabelRestDTO(
  val color: String,
  val description: String?,
  val descriptionHtml: String?,
  val id: Int,
  val name: String,
  val textColor: String
)