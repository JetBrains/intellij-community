// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.openapi.util.Key

interface GHPRToolWindowTabController {

  /**
   * Initial view that will be displayed after login and data pre-loading
   */
  var initialView: GHPRToolWindowInitialView

  val componentController: GHPRToolWindowTabComponentController?

  fun canResetRemoteOrAccount(): Boolean
  fun resetRemoteAndAccount()

  companion object {
    val KEY = Key.create<GHPRToolWindowTabController>("Github.PullRequests.ToolWindow.Tab.Controller")
  }
}