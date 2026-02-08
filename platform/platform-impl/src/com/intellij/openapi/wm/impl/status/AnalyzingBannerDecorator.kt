// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status

import com.intellij.help.impl.HelpManagerImpl
import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.status.ProcessPopup.hideSeparator
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.InlineBanner
import com.intellij.util.indexing.IndexingBundle
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.JPanel

/**
 * Places analyzing progress indicator on top, adds a banner under it.
 * Increases the popup minimum size when the banner appears and decreases it when it disappears.
 * So, the popup is large enough to fit the banner.
 */
internal class AnalyzingBannerDecorator(private val panel: JPanel, private val popupGetter: () -> JBPopup?, onBannerClose: Runnable) {

  /**
   * Component of analyzing progress,
   * placed above banner
   */
  private var analyzingComponent: Component? = null

  private val banner: Component by lazy { createBanner(onBannerClose) }

  private val popup: JBPopup?
    get() = popupGetter()

  fun indicatorAdded(indicator: ProgressComponent) {
    if (userClosedBanner()) return

    if (analyzingComponent == null && isAnalyzingIndicator(indicator)) {
      analyzingComponent = indicator.component
      panel.add(analyzingComponent, 0, 0)
      panel.add(banner, 1, 1)
    }
  }

  fun indicatorRemoved(indicator: ProgressComponent, isShowing: Boolean) {
    if (userClosedBanner()) return

    if (indicator.component == analyzingComponent) {
      analyzingComponent = null
      if (!isShowing) {
        removeBanner()
      }
    }
  }

  fun isBannerPresent(): Boolean = when {
    userClosedBanner() -> false
    else -> panel.components.contains(banner)
  }


  /**
   * Returns the height, required to fully display the banner and the analyzing component if present in progresses popup.
   */
  fun getPopupRequiredHeight(): Int {
    if (userClosedBanner()) return 0
    return banner.preferredSize.height + (analyzingComponent?.preferredSize?.height ?: 0) + JBUI.scale(28)
  }

  /**
   * Removes the banner and restores the default minimum size of the popup
   */
  private fun removeBanner() {
    panel.remove(banner)
    popup?.setMinimumSize(ProcessPopup.POPUP_MIN_SIZE)
    popup?.content?.minimumSize = null
  }

  /**
   * hides banner on popup close if analyzing completed
   */
  fun handlePopupClose() {
    if (userClosedBanner() || analyzingComponent != null) return
    removeBanner()
  }


  private fun createBanner(revalidatePanel: Runnable): Component {
    val banner = InlineBanner().apply {
      setMessage(IndexingBundle.message("progress.indexing.banner.text"))
      addAction(IdeBundle.message("link.learn.more")) {
        val url = HelpManagerImpl.getHelpUrl("Project-analysis")
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


private const val USER_CLOSED_ANALYZING_BANNER_KEY = "USER_CLOSED_ANALYZING_BANNER_KEY"

private fun userClosedBanner(): Boolean {
  val bannerWasClosed = PropertiesComponent.getInstance().getBoolean(USER_CLOSED_ANALYZING_BANNER_KEY, false)
  return bannerWasClosed || !Registry.`is`("analyzing.progress.explaining.banner.enable")
}