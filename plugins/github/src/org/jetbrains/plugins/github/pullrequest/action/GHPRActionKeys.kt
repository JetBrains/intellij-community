// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.DataKey
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.ui.list.GHPRListController
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRToolWindowProjectViewModel

object GHPRActionKeys {
  @JvmStatic
  val PULL_REQUEST_ID = DataKey.create<GHPRIdentifier>("org.jetbrains.plugins.github.pullrequest.id")

  @JvmStatic
  val PULL_REQUEST_URL = DataKey.create<String>("org.jetbrains.plugins.github.pullrequest.url")

  @JvmStatic
  val PULL_REQUESTS_LIST_CONTROLLER = DataKey.create<GHPRListController>("org.jetbrains.plugins.github.pullrequest.list.controller")

  @JvmStatic
  val PULL_REQUESTS_PROJECT_VM = DataKey.create<GHPRToolWindowProjectViewModel>(
    "org.jetbrains.plugins.github.pullrequest.project.vm")

  @JvmStatic
  val PULL_REQUEST_REPOSITORY = DataKey.create<GitRepository>("org.jetbrains.plugins.github.pullrequest.repository")

  @JvmStatic
  val PULL_REQUEST_DATA_PROVIDER = DataKey.create<GHPRDataProvider>("org.jetbrains.plugins.github.pullrequest.dataprovider")
}