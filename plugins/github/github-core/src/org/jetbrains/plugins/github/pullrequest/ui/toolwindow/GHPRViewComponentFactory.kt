// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.LoadingLabel
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDetailsComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsLoadingViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRDetailsViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRInfoViewModel
import javax.swing.JComponent

@ApiStatus.Internal
class GHPRViewComponentFactory(
  actionManager: ActionManager,
  private val project: Project,
  private val vm: GHPRInfoViewModel,
) {

  private val reloadDetailsAction = actionManager.getAction("Github.PullRequest.Details.Reload")

  fun create(cs: CoroutineScope): JComponent =
    cs.createInfoLoadingComponent().apply {
      registerDataProvider(this, vm)
    }

  private fun CoroutineScope.createInfoLoadingComponent(): JComponent {
    val cs = this
    return Wrapper(LoadingLabel()).apply {
      isOpaque = false
      background = UIUtil.getListBackground()

      bindContentIn(cs, vm.detailsVm) { result ->
        result.result?.fold(
          onSuccess = { createInfoComponent(it) },
          onFailure = { createInfoErrorComponent(vm, it) }
        ) ?: LoadingLabel()
      }
    }
  }

  private fun CoroutineScope.createInfoComponent(detailsVm: GHPRDetailsViewModel): JComponent {
    return GHPRDetailsComponentFactory.create(this,
                                              project,
                                              detailsVm).apply {
      reloadDetailsAction.registerCustomShortcutSet(this, nestedDisposable())
    }.let {
      CollaborationToolsUIUtil.wrapWithProgressStripe(this, detailsVm.isUpdating, it)
    }
  }

  companion object {
    fun registerDataProvider(component: JComponent, vm: GHPRInfoViewModel) {
      DataManager.registerDataProvider(component) { dataId ->
        when {
          GHPRActionKeys.PULL_REQUEST_ID.`is`(dataId) -> vm.pullRequest
          GHPRActionKeys.PULL_REQUEST_URL.`is`(dataId) -> vm.pullRequestUrl
          GHPRDetailsLoadingViewModel.DATA_KEY.`is`(dataId) -> vm
          else -> null
        }
      }
    }

    fun createInfoErrorComponent(vm: GHPRInfoViewModel, error: Throwable): JComponent {
      val errorPresenter = ErrorStatusPresenter.simple(
        GithubBundle.message("cannot.load.details"),
        actionProvider = vm.detailsLoadingErrorHandler::getActionForError
      )
      val errorPanel = ErrorStatusPanelFactory.create(error, errorPresenter)
      return CollaborationToolsUIUtil.moveToCenter(errorPanel)
    }
  }
}
