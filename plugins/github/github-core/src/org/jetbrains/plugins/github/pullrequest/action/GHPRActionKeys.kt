// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.action

import com.intellij.openapi.actionSystem.DataKey
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHPRConnectedProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.GHPRProjectViewModel
import org.jetbrains.plugins.github.pullrequest.ui.list.GHPRListController

@ApiStatus.Internal
object GHPRActionKeys {
  @JvmStatic
  val PULL_REQUEST_ID = DataKey.create<GHPRIdentifier>("org.jetbrains.plugins.github.pullrequest.id")

  @JvmStatic
  val PULL_REQUEST_URL = DataKey.create<String>("org.jetbrains.plugins.github.pullrequest.url")

  @JvmStatic
  val PULL_REQUESTS_LIST_CONTROLLER = DataKey.create<GHPRListController>("org.jetbrains.plugins.github.pullrequest.list.controller")

  @JvmStatic
  val PULL_REQUESTS_PROJECT_VM = DataKey.create<GHPRProjectViewModel>(
    "org.jetbrains.plugins.github.pullrequest.project.vm")

  @JvmStatic
  val PULL_REQUESTS_CONNECTED_PROJECT_VM = DataKey.create<GHPRConnectedProjectViewModel>(
    "org.jetbrains.plugins.github.pullrequest.connected.project.vm")
}