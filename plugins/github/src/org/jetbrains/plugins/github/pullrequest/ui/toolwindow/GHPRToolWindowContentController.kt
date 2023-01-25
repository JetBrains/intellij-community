// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.openapi.util.Key
import java.util.concurrent.CompletableFuture

/**
 * Controls the content of the whole PR toolwindow
 */
interface GHPRToolWindowContentController {

  val loginController: GHPRToolWindowLoginController
  val repositoryContentController: CompletableFuture<GHPRToolWindowRepositoryContentController>

  companion object {
    val KEY = Key.create<GHPRToolWindowContentController>("Github.PullRequests.ToolWindow.Content.Controller")
  }
}