// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.codeInsight.AutoPopupController
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.panels.Wrapper
import org.jetbrains.plugins.github.api.GithubApiRequestExecutorManager
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount
import org.jetbrains.plugins.github.pullrequest.data.GithubPullRequestsLoader
import org.jetbrains.plugins.github.util.GithubUrlUtil
import javax.swing.JComponent

class GithubPullRequestsComponentFactory(private val project: Project,
                                         private val progressManager: ProgressManager,
                                         private val requestExecutorManager: GithubApiRequestExecutorManager,
                                         private val actionManager: ActionManager,
                                         private val autoPopupController: AutoPopupController,
                                         private val popupFactory: JBPopupFactory) {

  fun createComponent(remoteUrl: String, account: GithubAccount): JComponent? {
    val loader = GithubPullRequestsLoader.create(project, progressManager, requestExecutorManager,
                                                 account, GithubUrlUtil.getUserAndRepositoryFromRemoteUrl(remoteUrl)!!) ?: return null
    val list = GithubPullRequestsListComponent(project, actionManager, autoPopupController, popupFactory, loader)
    Disposer.register(list, loader)

    return GithubPullRequestsComponent(list)
  }

  companion object {
    private class GithubPullRequestsComponent(list: GithubPullRequestsListComponent)
      : Wrapper(), Disposable {

      init {
        isFocusCycleRoot = true
        Disposer.register(this, list)
        setContent(list)
      }

      override fun dispose() {}
    }
  }
}