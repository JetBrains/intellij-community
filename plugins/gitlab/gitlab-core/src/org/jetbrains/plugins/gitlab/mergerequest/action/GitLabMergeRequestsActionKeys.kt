// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.openapi.actionSystem.DataKey
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabConnectedProjectViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.GitLabProjectViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsListViewModel

@ApiStatus.Internal
object GitLabMergeRequestsActionKeys {
  @JvmStatic
  val SELECTED: DataKey<GitLabMergeRequestDetails> = DataKey.create<GitLabMergeRequestDetails>("org.jetbrains.plugins.gitlab.mergerequest.selected")

  @JvmStatic
  val REVIEW_LIST_VM: DataKey<GitLabMergeRequestsListViewModel> =
    DataKey.create("org.jetbrains.plugins.gitlab.mergerequests.review.list.viewmodel")

  @JvmStatic
  val PROJECT_VM: DataKey<GitLabProjectViewModel> = DataKey.create("org.jetbrains.plugins.gitlab.mergerequest.project.vm")

  @JvmStatic
  val CONNECTED_PROJECT_VM: DataKey<GitLabConnectedProjectViewModel> = DataKey.create("org.jetbrains.plugins.gitlab.mergerequest.connected.project.vm")
}