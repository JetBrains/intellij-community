// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.ide.BrowserUtil
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.BannerStartPagePromoter
import com.intellij.ui.LicensingFacade
import com.intellij.ui.ScreenUtil
import com.intellij.util.messages.MessageBusConnection
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

  private var facadeConnection: MessageBusConnection? = null

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
    if (PropertiesComponent.getInstance().getBoolean(BUTTON_CLICKED_PROPERTY)) {
      return false
    }
    val facade = LicensingFacade.getInstance()
    if (facade?.isEvaluationLicense == true) {
      return false
    }
    if (ConfigImportHelper.isFirstSession() || ConfigImportHelper.isConfigImported()) {
      return false
    }

    val now = Calendar.getInstance()
    if (now.before(endDate) && now.after(startDate) || java.lang.Boolean.getBoolean("ignore.promo.dates")) {
      if (facade == null && facadeConnection == null) {
        val connection = ApplicationManager.getApplication().messageBus.connect()
        facadeConnection = connection
        connection.subscribe(LicensingFacade.LicenseStateListener.TOPIC, LicensingFacade.LicenseStateListener { facade ->
          if (facade == null) {
            return@LicenseStateListener
          }
          facadeConnection = null
          connection.disconnect()

          if (facade.isEvaluationLicense) {
            promoPanel?.let {
              it.isVisible = false
              it.revalidate()
            }
          }
        })
      }
      return true
    }
    return false
  }

  override fun onBannerHide() {
    if (promoPanel != null && ScreenUtil.isStandardAddRemoveNotify(promoPanel)) {
      facadeConnection?.disconnect()
      facadeConnection = null
    }
  }
}

internal object TechInsightsLabCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("tech.insights.lab.promoter", 1)

  val promoterShownEvent = GROUP.registerEvent("promoter.shown")

  val promoterButtonClicked = GROUP.registerEvent("promoter.button.clicked")

  override fun getGroup(): EventLogGroup = GROUP
}