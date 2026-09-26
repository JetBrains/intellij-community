// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.openapi.actionSystem.DataKey
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO

interface GitLabMergeRequestViewModel {
  val number: String
  val author: GitLabUserDTO
  val title: SharedFlow<@Nls String>
  val descriptionHtml: SharedFlow<@Nls String>
  val url: String

  fun reloadData()

  fun refreshData()

  companion object {
    val DATA_KEY = DataKey.create<GitLabMergeRequestViewModel>("GitLab.MergeRequest.Details.Controller")
  }
}