// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.StatusText
import com.intellij.vcs.log.ui.frame.ProgressStripe
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestShort
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHListLoader
import org.jetbrains.plugins.github.pullrequest.data.GHPRListUpdatesChecker
import org.jetbrains.plugins.github.pullrequest.data.GHPRSearchQuery
import org.jetbrains.plugins.github.ui.GHListLoaderPanel
import org.jetbrains.plugins.github.ui.HtmlInfoPanel
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import javax.swing.JComponent
import javax.swing.JPanel

internal class GHPRListLoaderPanel(private val listLoader: GHListLoader<GHPullRequestShort>,
                                   private val searchModel: SingleValueModel<String>,
                                   private val listUpdatesChecker: GHPRListUpdatesChecker,
                                   private val listReloadAction: RefreshAction,
                                   contentComponent: JComponent,
                                   filterComponent: JComponent)
  : GHListLoaderPanel(listLoader, contentComponent) {

  private lateinit var progressStripe: ProgressStripe

  override fun createCenterPanel(content: JComponent): JPanel {
    val stripe = ProgressStripe(content, this,
                                ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)
    progressStripe = stripe
    return stripe
  }

  override fun setLoading(isLoading: Boolean) {
    if (isLoading) progressStripe.startLoading() else progressStripe.stopLoading()
  }

  init {
    listUpdatesChecker.addOutdatedStateChangeListener(this) {
      updateInfoPanel()
    }

    addToTop(filterComponent)
  }

  override fun displayEmptyStatus(emptyText: StatusText) {
    if (searchModel.value.isNotEmpty()) {
      emptyText.text = GithubBundle.message("pull.request.list.no.matches")
      emptyText.appendSecondaryText(GithubBundle.message("pull.request.list.reset.filters"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
        searchModel.value = GHPRSearchQuery.DEFAULT.toString()
      }
    }
    else {
      emptyText.text = GithubBundle.message("pull.request.list.nothing.loaded")
      emptyText.appendSecondaryText(GithubBundle.message("pull.request.list.refresh"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
        listLoader.reset()
      }
    }
  }

  override fun updateInfoPanel() {
    super.updateInfoPanel()
    if (infoPanel.isEmpty && listUpdatesChecker.outdated) {
      infoPanel.setInfo("<html><body>${GithubBundle.message("pull.request.list.outdated")} <a href=''>${GithubBundle.message(
        "pull.request.list.refresh")}</a></body></html>",
                        HtmlInfoPanel.Severity.INFO) {
        ActionUtil.invokeAction(listReloadAction, this, ActionPlaces.UNKNOWN, it.inputEvent, null)
      }
    }
  }
}