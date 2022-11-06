// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.ui.welcomeScreen

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.wm.BannerStartPagePromoter
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeBalloonLayoutImpl
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import training.FeaturesTrainerIcons
import training.dsl.LessonUtil
import training.dsl.dropMnemonic
import training.lang.LangManager
import training.learn.CourseManager
import training.learn.LearnBundle
import training.learn.OpenLessonActivities
import training.ui.showOnboardingFeedbackNotification
import training.util.enableLessonsAndPromoters
import training.util.resetPrimaryLanguage
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel

private const val PROMO_HIDDEN = "ift.hide.welcome.screen.promo"

/** Do not use lesson itself in the parameters to postpone IFT modules/lessons initialization */
@ApiStatus.Internal
open class OnboardingLessonPromoter(@NonNls private val lessonId: String,
                                    @Nls private val lessonName: String,
                                    @NonNls private val languageName: String) : BannerStartPagePromoter() {
  override val promoImage: Icon
    get() = FeaturesTrainerIcons.PluginIcon

  override fun getPromotion(isEmptyState: Boolean): JPanel? {
    scheduleOnboardingFeedback()
    return super.getPromotion(isEmptyState)
  }
  override fun canCreatePromo(isEmptyState: Boolean): Boolean =
    enableLessonsAndPromoters &&
    !PropertiesComponent.getInstance().getBoolean(PROMO_HIDDEN, false) &&
    RecentProjectsManagerBase.getInstanceEx().getRecentPaths().size < 5

  override val headerLabel: String
    get() = LearnBundle.message("welcome.promo.header")

  override val actionLabel: String
    get() = LearnBundle.message("welcome.promo.start.tour")

  override fun runAction() =
    startOnboardingLessonWithSdk()

  override val description: String
    get() = LearnBundle.message("welcome.promo.description", LessonUtil.productName, languageName)

  private fun startOnboardingLessonWithSdk() {
    val lesson = CourseManager.instance.lessonsForModules.find { it.id == lessonId }
    if (lesson == null) {
      logger<OnboardingLessonPromoter>().error("No lesson with id $lessonId")
      return
    }
    val primaryLanguage: String = lesson.module.primaryLanguage?.primaryLanguage
                                  ?: error("No primary language for promoting lesson ${lesson.name}")
    resetPrimaryLanguage(primaryLanguage)
    LangManager.getInstance().getLangSupport()?.startFromWelcomeFrame { selectedSdk: Sdk? ->
      OpenLessonActivities.openOnboardingFromWelcomeScreen(lesson, selectedSdk)
    }
  }


  // A bit hacky way to schedule the onboarding feedback informer after the lesson was closed
  private fun scheduleOnboardingFeedback() {
    val langSupport = LangManager.getInstance().getLangSupport() ?: return
    val onboardingFeedbackData = langSupport.onboardingFeedbackData ?: return
    langSupport.onboardingFeedbackData = null

    invokeLater {
      showOnboardingFeedbackNotification(null, onboardingFeedbackData)
      (WelcomeFrame.getInstance()?.balloonLayout as? WelcomeBalloonLayoutImpl)?.showPopup()
    }
  }

  override val closeAction: ((JPanel) -> Unit) = { promoPanel ->
    PropertiesComponent.getInstance().setValue(PROMO_HIDDEN, true)
    promoPanel.removeAll()
    promoPanel.border = JBUI.Borders.empty(0, 2, 0, 0)
    promoPanel.isOpaque = false
    val text = LearnBundle.message("welcome.promo.close.hint",
                                   ActionsBundle.message("group.HelpMenu.text").dropMnemonic(),
                                   LearnBundle.message("action.ShowLearnPanel.text"), lessonName)
    promoPanel.add(JLabel("<html>$text</html>").also {
      it.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
      it.font = JBUI.Fonts.label().deriveFont(JBUI.Fonts.label().size2D + JBUIScale.scale(-1))
    })
    promoPanel.revalidate()
  }
}