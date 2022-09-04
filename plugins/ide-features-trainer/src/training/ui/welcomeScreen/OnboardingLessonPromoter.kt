// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.ui.welcomeScreen

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.wm.BannerStartPagePromoter
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeBalloonLayoutImpl
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
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
import java.awt.Color
import javax.swing.Icon
import javax.swing.JPanel

@ApiStatus.Internal
open class OnboardingLessonPromoter(@NonNls private val lessonId: String,
                                    @NonNls private val languageName: String) : BannerStartPagePromoter() {
  override fun promoImage(): Icon = FeaturesTrainerIcons.Img.PluginIcon

  override fun getPromotionForInitialState(): JPanel? {
    scheduleOnboardingFeedback()
    return super.getPromotionForInitialState()
  }

  override fun getHeaderLabel(): String =
    LearnBundle.message("welcome.promo.header")

  override fun getActionLabel(): String =
    LearnBundle.message("welcome.promo.start.tour")

  override fun runAction() =
    startOnboardingLessonWithSdk()

  override fun getDescription(): String =
    LearnBundle.message("welcome.promo.description", LessonUtil.productName, languageName)

  override fun outLineColor(): Color = UISettings.getInstance().separatorColor

  private fun startOnboardingLessonWithSdk() {
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