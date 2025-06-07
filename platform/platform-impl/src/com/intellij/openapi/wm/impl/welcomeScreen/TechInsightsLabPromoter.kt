// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.BannerStartPagePromoter
import com.intellij.openapi.wm.StartPagePromoter.Companion.PRIORITY_LEVEL_NORMAL
import com.intellij.ui.LicensingFacade
import com.intellij.util.PlatformUtils
import org.jetbrains.annotations.Nls
import java.util.*
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * @author Alexander Lobas
 */
internal class TechInsightsLabPromoter : BannerStartPagePromoter() {
  private val BUTTON_CLICKED_PROPERTY = "TECH_INSIGHTS_BUTTON_CLICKED"

  override val headerLabel: @Nls String = IdeBundle.message("tech.insights.lab.promoter.title")

  override val actionLabel: @Nls String = IdeBundle.message("tech.insights.lab.promoter.action")

  override val description: @Nls String = IdeBundle.message("tech.insights.lab.promoter.description")

  override val promoImage: Icon
    get() = IconLoader.getIcon("/images/mraPanelPromoBanner.png", TechInsightsLabPromoter::class.java)

  override val closeAction: ((JPanel) -> Unit) = { promoPanel ->
    PropertiesComponent.getInstance().setValue(BUTTON_CLICKED_PROPERTY, true)

    promoPanel.isVisible = false
    promoPanel.revalidate()
  }

  private var promoPanel: JComponent? = null

  private val startDate = Calendar.getInstance().also {
    it.set(2025, Calendar.APRIL, 1, 0, 1)
  }

  private val endDate = Calendar.getInstance().also {
    it.set(2025, Calendar.JUNE, 1, 0, 1)
  }

  override fun getPriorityLevel() = PRIORITY_LEVEL_NORMAL - 1

  override fun getPromotion(isEmptyState: Boolean): JComponent {
    val promotion = super.getPromotion(isEmptyState)
    promoPanel = promotion
    return promotion
  }

  override fun onBannerShown() {
    TechInsightsLabCollector.promoterShownEvent.log()
  }

  override fun runAction() {
    PropertiesComponent.getInstance().setValue(BUTTON_CLICKED_PROPERTY, true)
    TechInsightsLabCollector.promoterButtonClicked.log()
    BrowserUtil.browse("https://surveys.jetbrains.com/s3/ide-w-join-jetbrains-tech-insights-lab")

    promoPanel?.let {
      it.isVisible = false
      it.revalidate()
    }
  }

  override fun canCreatePromo(isEmptyState: Boolean): Boolean {
    if (PropertiesComponent.getInstance().getBoolean(BUTTON_CLICKED_PROPERTY) || !PlatformUtils.isJetBrainsProduct()) {
      return false
    }
    if (ConfigImportHelper.isFirstSession() || ConfigImportHelper.isConfigImported()) {
      return false
    }
    if (LicensingFacade.getInstance()?.isEvaluationLicense == true) {
      return false
    }

    val now = Calendar.getInstance()
    return now.before(endDate) && now.after(startDate) || java.lang.Boolean.getBoolean("ignore.promo.dates")
  }
}

internal object TechInsightsLabCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("tech.insights.lab.promoter", 1)

  val promoterShownEvent = GROUP.registerEvent("promoter.shown")

  val promoterButtonClicked = GROUP.registerEvent("promoter.button.clicked")

  override fun getGroup(): EventLogGroup = GROUP
}