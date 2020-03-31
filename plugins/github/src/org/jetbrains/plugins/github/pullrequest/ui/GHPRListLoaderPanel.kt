// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui

import com.intellij.ide.actions.RefreshAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.StatusText
import com.intellij.vcs.log.ui.frame.ProgressStripe
import org.jetbrains.plugins.github.pullrequest.data.GHPRListLoader
import org.jetbrains.plugins.github.ui.GHListLoaderPanel
import org.jetbrains.plugins.github.ui.HtmlInfoPanel
import javax.swing.JComponent
import javax.swing.JPanel

internal class GHPRListLoaderPanel(listLoader: GHPRListLoader,
                                   private val listReloadAction: RefreshAction,
                                   contentComponent: JComponent,
                                   filterComponent: JComponent)
  : GHListLoaderPanel<GHPRListLoader>(listLoader, contentComponent), Disposable {

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
    listLoader.addOutdatedStateChangeListener(this) {
      updateInfoPanel()
    }

    addToTop(filterComponent)
    resetFilter()
  }

  override fun displayEmptyStatus(emptyText: StatusText) {
    if (listLoader.filterNotEmpty) {
      emptyText.text = "No pull requests matching filters. "
      emptyText.appendSecondaryText("Reset Filters", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
        resetFilter()
      }
    }
    else {
      emptyText.text = "No pull requests loaded. "
      emptyText.appendSecondaryText("Refresh", SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
        listLoader.reset()
      }
    }
  }

  private fun resetFilter() {
    listLoader.resetFilter()
  }

  override fun updateInfoPanel() {
    super.updateInfoPanel()
    if (infoPanel.isEmpty && listLoader.outdated) {
      infoPanel.setInfo("<html><body>The list is outdated. <a href=''>Refresh</a></body></html>",
                        HtmlInfoPanel.Severity.INFO) {
        ActionUtil.invokeAction(listReloadAction, this, ActionPlaces.UNKNOWN, it.inputEvent, null)
      }
    }
  }
}