// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

// GL 15 and higher
data class GitLabServerMetadataDTO(
  val version: String,
  val revision: String,
  //val kas: Any, - Kubernetes data
  val enterprise: Boolean
)
