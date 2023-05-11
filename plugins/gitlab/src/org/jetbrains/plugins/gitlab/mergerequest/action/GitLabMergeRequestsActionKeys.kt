// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.openapi.actionSystem.DataKey
import git4idea.repo.GitRepository
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.mergerequest.file.GitLabMergeRequestsFilesController
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestsListViewModel

internal object GitLabMergeRequestsActionKeys {
  @JvmStatic
  val SELECTED = DataKey.create<GitLabMergeRequestDetails>("org.jetbrains.plugins.gitlab.mergerequest.selected")

  @JvmStatic
  val FILES_CONTROLLER = DataKey.create<GitLabMergeRequestsFilesController>("org.jetbrains.plugins.gitlab.mergerequests.files.controller")

  @JvmStatic
  val REVIEW_LIST_VM: DataKey<GitLabMergeRequestsListViewModel> =
    DataKey.create("org.jetbrains.plugins.gitlab.mergerequests.review.list.viewmodel")

  @JvmStatic
  val REVIEW_DETAILS_LOADING_VM: DataKey<GitLabMergeRequestDetailsLoadingViewModel> =
    DataKey.create("org.jetbrains.plugins.gitlab.mergerequests.review.details.loading.viewmodel")

  @JvmStatic
  val GIT_REPOSITORY: DataKey<GitRepository> =
    DataKey.create("org.jetbrains.plugins.gitlab.mergerequests.git.repository")

  @JvmStatic
  val MERGE_REQUEST: DataKey<GitLabMergeRequest> =
    DataKey.create("org.jetbrains.plugins.gitlab.mergerequests.review.mergerequest")
}