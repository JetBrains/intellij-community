// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.openapi.actionSystem.DataKey
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestDetails
import org.jetbrains.plugins.gitlab.mergerequest.file.GitLabMergeRequestsFilesController
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
  val REVIEW_BRANCH_VM: DataKey<CodeReviewBranchesViewModel> =
    DataKey.create("org.jetbrains.plugins.gitlab.mergerequests.review.branch.viewmodel")
}