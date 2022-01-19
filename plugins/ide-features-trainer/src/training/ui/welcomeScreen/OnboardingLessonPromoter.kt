// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.ui.welcomeScreen

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.StartPagePromoter
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeBalloonLayoutImpl
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import training.FeaturesTrainerIcons
import training.dsl.LessonUtil
import training.lang.LangManager
import training.learn.CourseManager
import training.learn.LearnBundle
import training.learn.OpenLessonActivities
import training.ui.UISettings
import training.ui.showOnboardingFeedbackNotification
import training.util.resetPrimaryLanguage
import training.util.rigid
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Rectangle
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.border.MatteBorder

@ApiStatus.Internal
open class OnboardingLessonPromoter(@NonNls private val lessonId: String, @NonNls private val languageName: String) : StartPagePromoter {
  open fun promoImage(): Icon = FeaturesTrainerIcons.Img.PluginIcon

  override fun getPromotionForInitialState(): JPanel? {
    scheduleOnboardingFeedback()
    val rPanel: JPanel = NonOpaquePanel()
    rPanel.layout = BoxLayout(rPanel, BoxLayout.PAGE_AXIS)
    rPanel.border = JBUI.Borders.empty(JBUI.scale(10), JBUI.scale(32))

    val vPanel: JPanel = NonOpaquePanel()
    vPanel.layout = BoxLayout(vPanel, BoxLayout.PAGE_AXIS)
    vPanel.alignmentY = Component.TOP_ALIGNMENT

    val header = JLabel(LearnBundle.message("welcome.promo.header"))
    header.font = StartupUiUtil.getLabelFont().deriveFont(Font.BOLD).deriveFont(StartupUiUtil.getLabelFont().size2D + JBUI.scale(4))
    vPanel.add(header)
    vPanel.add(rigid(0, 4))
    val description = JLabel("<html>${LearnBundle.message("welcome.promo.description", LessonUtil.productName, languageName)}</html>").also {
      it.font = JBUI.Fonts.label().deriveFont(JBUI.Fonts.label().size2D + (when {
        SystemInfo.isLinux -> JBUIScale.scale(-2)
        SystemInfo.isMac -> JBUIScale.scale(-1)
        else -> 0
      }))
      it.foreground = UIUtil.getContextHelpForeground()
    }
    vPanel.add(description)
    val jButton = JButton()
    jButton.isOpaque = false
    jButton.action = object : AbstractAction(LearnBundle.message("welcome.promo.start.tour")) {
      override fun actionPerformed(e: ActionEvent?) {
        startOnboardingLessonWithSdk()
      }
    }
    vPanel.add(rigid(0, 18))
    vPanel.add(buttonPixelHunting(jButton))

    val hPanel: JPanel = NonOpaquePanel()
    hPanel.layout = BoxLayout(hPanel, BoxLayout.X_AXIS)
    hPanel.add(vPanel)
    hPanel.add(Box.createHorizontalGlue())
    hPanel.add(rigid(20, 0))
    val picture = JLabel(promoImage())
    picture.alignmentY = Component.TOP_ALIGNMENT
    hPanel.add(picture)

    rPanel.add(NonOpaquePanel().apply {
      border = MatteBorder(JBUI.scale(1), 0, 0, 0, UISettings.instance.separatorColor)
    })
    rPanel.add(rigid(0, 20))
    rPanel.add(hPanel)
    return rPanel
  }

  protected fun startOnboardingLessonWithSdk() {
    val lesson = CourseManager.instance.lessonsForModules.find { it.id == lessonId }
    if (lesson == null) {
      logger<OnboardingLessonPromoter>().error("No lesson with id $lessonId")
      return
    }
    val primaryLanguage = lesson.module.primaryLanguage ?: error("No primary language for promoting lesson ${lesson.name}")
    resetPrimaryLanguage(primaryLanguage)
    LangManager.getInstance().getLangSupport()?.startFromWelcomeFrame { selectedSdk: Sdk? ->
      OpenLessonActivities.openOnboardingFromWelcomeScreen(lesson, selectedSdk)
    }
  }

  private fun buttonPixelHunting(button: JButton): JPanel {

    val buttonSizeWithoutInsets = Dimension(button.preferredSize.width - button.insets.left - button.insets.right,
                                            button.preferredSize.height - button.insets.top - button.insets.bottom)

    val buttonPlace = JPanel().apply {
      layout = null
      maximumSize = buttonSizeWithoutInsets
      preferredSize = buttonSizeWithoutInsets
      minimumSize = buttonSizeWithoutInsets
      isOpaque = false
      alignmentX = JPanel.LEFT_ALIGNMENT
    }

    buttonPlace.add(button)
    button.bounds = Rectangle(-button.insets.left, -button.insets.top, button.preferredSize.width, button.preferredSize.height)

    return buttonPlace
  }

  // A bit hacky way to schedule the onboarding feedback informer after the lesson was closed
  private fun scheduleOnboardingFeedback() {
    val langSupport = LangManager.getInstance().getLangSupport() ?: return

    val onboardingFeedbackData = langSupport.onboardingFeedbackData ?: return

    invokeLater {
      langSupport.onboardingFeedbackData = null
      showOnboardingFeedbackNotification(null, onboardingFeedbackData)
      (WelcomeFrame.getInstance()?.balloonLayout as? WelcomeBalloonLayoutImpl)?.showPopup()
    }
  }
}