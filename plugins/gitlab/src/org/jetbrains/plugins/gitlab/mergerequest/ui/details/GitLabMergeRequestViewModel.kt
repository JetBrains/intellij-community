// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.openapi.actionSystem.DataKey
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO

interface GitLabMergeRequestViewModel {
  val number: String
  val author: GitLabUserDTO
  val title: Flow<@Nls String>
  val descriptionHtml: Flow<@Nls String>
  val url: String

  fun refreshData()

  companion object {
    val DATA_KEY = DataKey.create<GitLabMergeRequestViewModel>("GitLab.MergeRequest.Details.Controller")
  }
}