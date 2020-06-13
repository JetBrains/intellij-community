// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.DataKey
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRViewController

object GHPRActionKeys {
  @JvmStatic
  val GIT_REPOSITORY = DataKey.create<GitRepository>("org.jetbrains.plugins.github.pullrequest.git.repository")

  @JvmStatic
  val PULL_REQUEST_DATA_PROVIDER = DataKey.create<GHPRDataProvider>("org.jetbrains.plugins.github.pullrequest.data.provider")

  @JvmStatic
  val SELECTED_PULL_REQUEST = DataKey.create<GHPullRequestShort>("org.jetbrains.plugins.github.pullrequest.list.selected")

  @JvmStatic
  val PULL_REQUESTS_CONTROLLER = DataKey.create<GHPRViewController>("org.jetbrains.plugins.github.pullrequest.view.controller")
}