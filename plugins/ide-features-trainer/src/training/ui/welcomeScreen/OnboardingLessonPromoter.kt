// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.ui.welcomeScreen

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.wm.BannerStartPagePromoter
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.concurrency.annotations.RequiresEdt
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
import training.learn.NewUsersOnboardingExperimentAccessor
import training.learn.OpenLessonActivities
import training.learn.lesson.LessonState
import training.learn.lesson.LessonStateManager
import training.statistic.StatisticBase
import training.ui.showOnboardingFeedbackNotification
import training.util.enableLessonsAndPromoters
import training.util.resetPrimaryLanguage
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel

private const val PROMO_HIDDEN = "ift.hide.welcome.screen.promo"

/** Do not use lesson itself in the parameters to postpone IFT modules/lessons initialization */
@ApiStatus.Internal
open class OnboardingLessonPromoter(@NonNls protected val lessonId: String,
                                    @NonNls private val languageId: String,
                                    @Nls private val lessonName: String) : BannerStartPagePromoter() {
  override val promoImage: Icon
    get() = FeaturesTrainerIcons.PluginIcon

  override fun canCreatePromo(isEmptyState: Boolean): Boolean {
    val notificationScheduled = scheduleOnboardingFeedback()
    return enableLessonsAndPromoters &&
           !notificationScheduled &&
           !PropertiesComponent.getInstance().getBoolean(PROMO_HIDDEN, false) &&
           RecentProjectsManagerBase.getInstanceEx().getRecentPaths().size < 5 &&
           LessonStateManager.getStateFromBase(lessonId) == LessonState.NOT_PASSED &&
           !NewUsersOnboardingExperimentAccessor.isExperimentEnabled()
  }

  override val headerLabel: String
    get() = LearnBundle.message("welcome.promo.header")

  override val actionLabel: String
    get() = LearnBundle.message("welcome.promo.start.tour")

  override fun runAction() =
    startOnboardingLessonWithSdk(lessonId, languageId)

  override val description: String
    get() = LearnBundle.message("welcome.promo.description", LessonUtil.productName)

  @RequiresEdt
  protected fun startOnboardingLessonWithSdk(lessonId: String, languageId: String) {
    resetPrimaryLanguage(languageId)
    val lesson = CourseManager.instance.lessonsForModules.find { it.id == lessonId }
    if (lesson == null) {
      logger<OnboardingLessonPromoter>().error("No lesson with id $lessonId")
      return
    }
    LangManager.getInstance().getLangSupport()?.startFromWelcomeFrame { selectedSdk: Sdk? ->
      OpenLessonActivities.openOnboardingFromWelcomeScreen(lesson, selectedSdk)
    }
  }


  // A bit hacky way to schedule the onboarding feedback informer after the lesson was closed
  private fun scheduleOnboardingFeedback(): Boolean {
    val langSupport = LangManager.getInstance().getLangSupport() ?: return false
    val onboardingFeedbackData = langSupport.onboardingFeedbackData ?: return false
    langSupport.onboardingFeedbackData = null
    invokeLater {
      showOnboardingFeedbackNotification(null, onboardingFeedbackData)
    }
    return true
  }

  override val closeAction: ((JPanel) -> Unit) = { promoPanel ->
    PropertiesComponent.getInstance().setValue(PROMO_HIDDEN, true)
    promoPanel.removeAll()
    promoPanel.border = JBUI.Borders.emptyLeft(2)
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

  override fun onBannerShown() {
    StatisticBase.logOnboardingBannerShown(lessonId, languageId)
  }
}