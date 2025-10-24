// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.help.impl.HelpManagerImpl
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.status.ProcessPopup.hideSeparator
import com.intellij.ui.ClientProperty
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.InlineBanner
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.JPanel

/*
 Places analyzing progress indicator on top, adds a banner under it.
 Also handles separators:
  - There is no separator above and below banner
  - When banner is removed, the separator between the first 2 progresses should appear
 */
internal class AnalyzingBannerDecorator(private val panel: JPanel, revalidatePanel: Runnable) {

  // component of analyzing progress,
  // placed above banner
  private var analyzingComponent: Component? = null

  private val banner: Component = createBanner(revalidatePanel)


  fun indicatorAdded(indicator: ProgressComponent) {
    if (userClosedBanner()) return

    if (isAnalyzingIndicator(indicator) && analyzingComponent == null) {
      analyzingComponent = indicator.component
      hideSeparator(analyzingComponent!!)

      panel.add(analyzingComponent, 0, 0)
      panel.add(banner, 1, 1)
    }
    hideSeparatorAfterBanner()
  }

  fun indicatorRemoved(indicator: ProgressComponent, isShowing: Boolean) {
    if (userClosedBanner()) return

    if (indicator.component == analyzingComponent) {
      analyzingComponent = null
      if (!isShowing) {
        removeBanner()
      }
    }
    hideSeparatorAfterBanner()
  }

  // hides banner on popup close if analyzing completed
  fun handlePopupClose() {
    if (userClosedBanner() || analyzingComponent != null) {
      return
    }
    removeBanner()
  }

  // removes the separator between the first 2 indicators if the banner is shown
  private fun hideSeparatorAfterBanner() {
    val index = panel.getComponentZOrder(banner)
    if (index == -1) return
    if (panel.componentCount < index + 2) return
    hideSeparator(panel.getComponent(index + 1))
  }

  // removes the banner and inserts the separator between the first 2 indicators
  private fun removeBanner() {
    panel.remove(banner)

    if (analyzingComponent != null && panel.componentCount > 1) {
      showSeparator(panel.getComponent(1))
    }
  }

  private fun createBanner(revalidatePanel: Runnable): Component {
    val banner = InlineBanner().apply {
      setMessage(IndexingBundle.message("progress.indexing.banner.text"))
      addAction(IdeBundle.message("link.learn.more")) {
        val url = HelpManagerImpl.getHelpUrl("Indexing")
        if (url != null) {
          BrowserUtil.browse(url)
        }
      }

      if (ExperimentalUI.isNewUI()) {
        setOpaque(false)
      }

      // removes banner and prevents showing it again
      setCloseAction {
        removeBanner()
        PropertiesComponent.getInstance().setValue(USER_CLOSED_ANALYZING_BANNER_KEY, true)
        revalidatePanel.run()
      }
    }

    // setting border for InlineBanner, sets it for inner content, not for the outside as we want
    val panel = JBUI.Panels.simplePanel(banner).apply {
      border = JBUI.Borders.empty(8, 12)
      hideSeparator(this)
      if (ExperimentalUI.isNewUI()) {
        setOpaque(false)
      }
    }

    return panel
  }
}

private fun isAnalyzingIndicator(indicator: ProgressComponent): Boolean {
  return indicator.info.title == IndexingBundle.message("progress.indexing")
}


private fun showSeparator(component: Component) {
  val panel = ClientProperty.get(component, ProcessPopup.KEY)
  panel?.setSeparatorEnabled(true)
}


private const val USER_CLOSED_ANALYZING_BANNER_KEY = "USER_CLOSED_ANALYZING_BANNER_KEY"

private fun userClosedBanner(): Boolean {
  val bannerWasClosed = PropertiesComponent.getInstance().getBoolean(USER_CLOSED_ANALYZING_BANNER_KEY, false)
  return bannerWasClosed || !Registry.`is`("analyzing.progress.explaining.banner.enable")
}