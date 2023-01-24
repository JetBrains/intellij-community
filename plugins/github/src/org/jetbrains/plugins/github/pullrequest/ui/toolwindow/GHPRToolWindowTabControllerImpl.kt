// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.content.Content
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.config.GithubPullRequestsProjectUISettings
import org.jetbrains.plugins.github.util.GHHostedRepositoriesManager
import java.awt.BorderLayout
import java.util.concurrent.CompletableFuture
import javax.swing.JPanel

internal class GHPRToolWindowTabControllerImpl(scope: CoroutineScope,
                                               private val project: Project,
                                               private val tabVm: GHPRToolWindowTabViewModel,
                                               private val content: Content) : GHPRToolWindowTabController {

  private val cs = scope.childScope(Dispatchers.Main.immediate)

  private var _componentController = CompletableFuture<GHPRToolWindowTabComponentController>()
  override val componentController: CompletableFuture<GHPRToolWindowTabComponentController>
    get() = _componentController

  init {
    cs.launch {
      tabVm.viewState.collectScoped { scope, vm ->
        content.displayName = GithubBundle.message("toolwindow.stripe.Pull_Requests")
        when (vm) {
          is GHPRTabContentViewModel.Selectors -> {
            _componentController.cancel(true)
            _componentController = CompletableFuture()
            showSelectors(scope, vm)
          }
          is GHPRTabContentViewModel.PullRequests -> {
            val controller = showPullRequests(scope, vm)
            _componentController.complete(controller)
          }
        }
      }
    }
  }

  private fun showSelectors(scope: CoroutineScope, vm: GHPRTabContentViewModel.Selectors) {
    val selector = GHRepositoryAndAccountSelectorComponentFactory(project, vm.selectorVm, service<GHAccountManager>()).create(scope)
    val component = JPanel(BorderLayout()).apply {
      add(selector, BorderLayout.NORTH)
    }
    CollaborationToolsUIUtil.setComponentPreservingFocus(content, component)
  }

  private fun showPullRequests(scope: CoroutineScope, vm: GHPRTabContentViewModel.PullRequests): GHPRToolWindowTabComponentController {
    val wrapper = Wrapper()
    val controller = GHPRToolWindowTabComponentControllerImpl(project, project.service<GHHostedRepositoriesManager>(),
                                                              project.service<GithubPullRequestsProjectUISettings>(),
                                                              vm.connection.dataContext, wrapper, scope.nestedDisposable()) {
      content.displayName = it
    }
    CollaborationToolsUIUtil.setComponentPreservingFocus(content, wrapper)
    return controller
  }

  override fun canResetRemoteOrAccount(): Boolean = tabVm.canSelectDifferentRepoOrAccount()

  override fun resetRemoteAndAccount() = tabVm.selectDifferentRepoOrAccount()
}
