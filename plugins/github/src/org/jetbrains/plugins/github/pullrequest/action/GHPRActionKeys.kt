// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreviewModel
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.list.GHPRListController
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRToolWindowRepositoryContentController

object GHPRActionKeys {
  @JvmStatic
  val GIT_REPOSITORY = DataKey.create<GitRepository>("org.jetbrains.plugins.github.pullrequest.git.repository")

  @JvmStatic
  val PULL_REQUEST_DATA_PROVIDER = DataKey.create<GHPRDataProvider>("org.jetbrains.plugins.github.pullrequest.data.provider")

  internal val PULL_REQUEST_FILES = DataKey.create<Iterable<FilePath>>("org.jetbrains.plugins.github.pullrequest.files")

  @JvmStatic
  val SELECTED_PULL_REQUEST = DataKey.create<GHPullRequestShort>("org.jetbrains.plugins.github.pullrequest.list.selected")

  @JvmStatic
  val PULL_REQUESTS_LIST_CONTROLLER = DataKey.create<GHPRListController>("org.jetbrains.plugins.github.pullrequest.list.controller")

  @JvmStatic
  val PULL_REQUESTS_CONTENT_CONTROLLER = DataKey.create<GHPRToolWindowRepositoryContentController>(
    "org.jetbrains.plugins.github.pullrequest.content.controller")

  @JvmStatic
  val COMBINED_DIFF_PREVIEW_MODEL = DataKey.create<CombinedDiffPreviewModel>(
    "org.jetbrains.plugins.github.pullrequest.combined.diff.preview.model")
}