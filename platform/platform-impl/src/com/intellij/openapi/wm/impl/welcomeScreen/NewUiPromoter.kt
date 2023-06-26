// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.experimental.ExperimentalUiCollector
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.BannerStartPagePromoter
import com.intellij.openapi.wm.StartPagePromoter.Companion.PRIORITY_LEVEL_HIGH
import com.intellij.ui.ExperimentalUI
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.*

@ApiStatus.Internal
class NewUiPromoter : BannerStartPagePromoter() {

  override val promoImage: Icon
    get() = IconLoader.getIcon("welcome/newUiPromo.png", NewUiPromoter::class.java.classLoader)

  override fun canCreatePromo(isEmptyState: Boolean): Boolean {
    val propertyComponent = PropertiesComponent.getInstance()
    return !ExperimentalUI.isNewUI() &&
           !propertyComponent.getBoolean(ExperimentalUI.NEW_UI_USED_PROPERTY) &&
           !propertyComponent.getBoolean(ExperimentalUI.NEW_UI_PROMO_BANNER_DISABLED_PROPERTY)
  }

  override fun getPriorityLevel(): Int = PRIORITY_LEVEL_HIGH

  override val headerLabel: String
    get() = IdeBundle.message("welcome.expUi.promo.header")

  override val actionLabel: String
    get() = IdeBundle.message("welcome.expUi.promo.button")

  override fun runAction() {
    ExperimentalUiCollector.logSwitchUi(ExperimentalUiCollector.SwitchSource.WELCOME_PROMO, true)
    ExperimentalUI.setNewUI(true)
  }

  override fun getPromotion(isEmptyState: Boolean): JComponent {
    ExperimentalUiCollector.inviteBannerShown.log()
    return super.getPromotion(isEmptyState)
  }

  override val description: String
    get() = IdeBundle.message("welcome.expUi.promo.description", WelcomeScreenComponentFactory.getAppName())

  override val closeAction: ((JPanel) -> Unit) = { panel ->
    ExperimentalUiCollector.inviteBannerClosed.log()
    PropertiesComponent.getInstance().setValue(ExperimentalUI.NEW_UI_PROMO_BANNER_DISABLED_PROPERTY, true)
    panel.isVisible = false
  }

  override fun createHeader(): JLabel {
    return super.createHeader().apply {
      horizontalTextPosition = SwingConstants.LEFT
      iconTextGap = JBUI.scale(6)
      icon = AllIcons.General.Beta
    }
  }
}
