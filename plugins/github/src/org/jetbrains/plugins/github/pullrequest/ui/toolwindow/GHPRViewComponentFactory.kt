// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.LoadingLabel
import com.intellij.collaboration.ui.codereview.CodeReviewProgressTreeModelFromDetails
import com.intellij.collaboration.ui.codereview.changes.CodeReviewChangeListComponentFactory
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDetailsComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRChangeListViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsLoadingViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRChangesViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRDetailsViewModel
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.model.GHPRInfoViewModel
import javax.swing.JComponent

internal class GHPRViewComponentFactory(actionManager: ActionManager,
                                        private val project: Project,
                                        private val vm: GHPRInfoViewModel) {

  private val reloadDetailsAction = actionManager.getAction("Github.PullRequest.Details.Reload")

  fun create(cs: CoroutineScope): JComponent =
    cs.createInfoLoadingComponent().apply {
      DataManager.registerDataProvider(this) { dataId ->
        when {
          GHPRActionKeys.PULL_REQUEST_ID.`is`(dataId) -> vm.pullRequest
          GHPRActionKeys.PULL_REQUEST_URL.`is`(dataId) -> vm.pullRequestUrl
          GHPRDetailsLoadingViewModel.DATA_KEY.`is`(dataId) -> vm
          else -> null
        }
      }
    }

  private fun CoroutineScope.createInfoLoadingComponent(): JComponent {
    val cs = this
    return Wrapper(LoadingLabel()).apply {
      isOpaque = false
      background = UIUtil.getListBackground()

      bindContentIn(cs, vm.detailsVm) { result ->
        result.result?.fold(
          onSuccess = { createInfoComponent(it) },
          onFailure = { createInfoErrorComponent(it) }
        ) ?: LoadingLabel()
      }
    }
  }

  private fun CoroutineScope.createInfoComponent(detailsVm: GHPRDetailsViewModel): JComponent {
    return GHPRDetailsComponentFactory.create(this,
                                              project,
                                              detailsVm,
                                              createChangesComponent(detailsVm.changesVm)).apply {
      reloadDetailsAction.registerCustomShortcutSet(this, nestedDisposable())
    }.let {
      CollaborationToolsUIUtil.wrapWithProgressStripe(this, detailsVm.isUpdating, it)
    }
  }

  private fun createInfoErrorComponent(error: Throwable): JComponent {
    val errorPresenter = ErrorStatusPresenter.simple(
      GithubBundle.message("cannot.load.details"),
      actionProvider = vm.detailsLoadingErrorHandler::getActionForError
    )
    val errorPanel = ErrorStatusPanelFactory.create(error, errorPresenter)
    return CollaborationToolsUIUtil.moveToCenter(errorPanel)
  }

  private fun CoroutineScope.createChangesComponent(changesVm: GHPRChangesViewModel): JComponent {
    val cs = this
    return Wrapper(LoadingLabel()).apply {
      bindContentIn(cs, changesVm.changeListVm) { res ->
        res.result?.let {
          it.fold(onSuccess = {
            createChangesPanel(it)
          }, onFailure = {
            createChangesErrorComponent(changesVm, it)
          })
        } ?: LoadingLabel()
      }
    }
  }

  private fun CoroutineScope.createChangesPanel(changeListVm: GHPRChangeListViewModel): JComponent {
    val progressModel = CodeReviewProgressTreeModelFromDetails(this, changeListVm)
    val tree = CodeReviewChangeListComponentFactory.createIn(this, changeListVm, progressModel,
                                                             GithubBundle.message("pull.request.does.not.contain.changes"))

    val scrollPane = ScrollPaneFactory.createScrollPane(tree, true)

    DataManager.registerDataProvider(scrollPane) { dataId ->
      when {
        tree.isShowing ->
          when {
            GHPRChangeListViewModel.DATA_KEY.`is`(dataId) -> changeListVm
            CodeReviewChangeListViewModel.DATA_KEY.`is`(dataId) -> changeListVm
            else -> null
          } ?: tree.getData(dataId)
        else -> null
      }
    }
    tree.installPopupHandler(ActionManager.getInstance().getAction("Github.PullRequest.Changes.Popup") as ActionGroup)

    return scrollPane
  }

  private fun createChangesErrorComponent(changesVm: GHPRChangesViewModel, error: Throwable): JComponent {
    val errorPresenter = ErrorStatusPresenter.simple(
      GithubBundle.message("cannot.load.changes"),
      actionProvider = changesVm.changesLoadingErrorHandler::getActionForError
    )
    val errorPanel = ErrorStatusPanelFactory.create(error, errorPresenter)
    return CollaborationToolsUIUtil.moveToCenter(errorPanel)
  }
}
