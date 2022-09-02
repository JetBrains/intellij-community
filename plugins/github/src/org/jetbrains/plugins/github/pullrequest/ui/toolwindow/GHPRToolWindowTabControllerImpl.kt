// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.ClientProperty
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.content.Content
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingPanelFactory
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class GHPRToolWindowTabControllerImpl(scope: CoroutineScope,
                                               private val project: Project,
                                               private val tabVm: GHPRToolWindowTabViewModel,
                                               private val content: Content) :
  GHPRToolWindowTabController {

  override var initialView = GHPRToolWindowViewType.LIST
  override val componentController: GHPRToolWindowTabComponentController?
    get() = ClientProperty.findInHierarchy(content.component, GHPRToolWindowTabComponentController.KEY)

  init {
    scope.launch {
      tabVm.viewState.collectScoped { scope, vm ->
        content.displayName = GithubBundle.message("toolwindow.stripe.Pull_Requests")

        val nestedComponent = if (vm != null) {
          createNestedComponent(scope, vm)
        }
        else {
          JPanel(null)
        }
        setComponentPreservingFocus(content, nestedComponent)
      }
    }
  }

  private fun createNestedComponent(scope: CoroutineScope, vm: GHPRTabContentViewModel) = when (vm) {
    is GHPRTabContentViewModel.Selectors -> {
      val selector = GHRepositoryAndAccountSelectorComponentFactory(project, vm.selectorVm, service()).create(scope)
      JPanel(BorderLayout()).apply {
        add(selector, BorderLayout.NORTH)
      }
    }
    is GHPRTabContentViewModel.PullRequests -> {
      GHLoadingPanelFactory(vm.loadingModel, null, GithubBundle.message("cannot.load.data.from.github"),
                            GHApiLoadingErrorHandler(project, vm.account) {
                              vm.reloadContext()
                            }).create { parent, ctx ->
        val wrapper = Wrapper()
        GHPRToolWindowTabComponentControllerImpl(project,
                                                 project.service(),
                                                 project.service(),
                                                 ctx, wrapper, scope.nestedDisposable(),
                                                 initialView) {
          content.displayName = it
        }.also {
          ClientProperty.put(parent, GHPRToolWindowTabComponentController.KEY, it)
        }
        wrapper
      }
    }
  }

  override fun canResetRemoteOrAccount(): Boolean = tabVm.canSelectDifferentRepoOrAccount()

  override fun resetRemoteAndAccount() = tabVm.selectDifferentRepoOrAccount()
}

private fun setComponentPreservingFocus(content: Content, component: JComponent) {
  val focused = CollaborationToolsUIUtil.isFocusParent(content.component)
  content.component = component
  if (focused) {
    CollaborationToolsUIUtil.focusPanel(content.component)
  }
}
