// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.ui

import com.intellij.ide.util.TipAndTrickBean
import com.intellij.ide.util.TipAndTrickPromotionFactory
import com.intellij.ide.util.TipDialog
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import training.FeaturesTrainerIcons
import training.learn.CourseManager
import training.learn.LearnBundle
import training.learn.course.Lesson
import training.statistic.LessonStartingWay
import training.statistic.StatisticBase
import training.util.RoundedPanel
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel

class IftTipAndTrickPromoter : TipAndTrickPromotionFactory {
  override fun createPromotionPanel(project: Project, tip: TipAndTrickBean): JPanel? {
    val lesson = findLessonForTip(tip) ?: return null
    return createOpenLessonPanel(project, lesson, tip)
  }

  private fun findLessonForTip(tip: TipAndTrickBean): Lesson? {
    val tipId = tip.fileName.removePrefix("neue-").removeSuffix(".html")
    val courseManager = CourseManager.instance
    val lessons = courseManager.lessonsForModules.filter { it.suitableTips.contains(tipId) }
    if (lessons.isNotEmpty()) {
      if (lessons.size > 1) {
        thisLogger().warn("$tip declared as suitable in more than one lesson: $lessons")
      }
      return lessons[0]
    }
    return null
  }

  private fun createOpenLessonPanel(project: Project, lesson: Lesson, tip: TipAndTrickBean): JPanel {
    val container = RoundedPanel(8)
    container.layout = BoxLayout(container, BoxLayout.X_AXIS)
    container.background = UISettings.getInstance().shortcutBackgroundColor

    val promotionLabel = JLabel(LearnBundle.message("tip.and.trick.promotion.label"))
    promotionLabel.icon = FeaturesTrainerIcons.Img.FeatureTrainerBanner
    promotionLabel.iconTextGap = JBUI.scale(6)
    container.add(Box.createRigidArea(JBDimension(12, 28)))
    container.add(promotionLabel)
    container.add(Box.createHorizontalGlue())

    val openLessonLink = ActionLink(LearnBundle.message("tip.and.trick.promotion.open.lesson")) {
      TipDialog.hideForProject(project)
      if (!project.isDisposed) {
        CourseManager.instance.openLesson(project, lesson, LessonStartingWay.TIP_AND_TRICK_PROMOTER, forceStartLesson = true)
        StatisticBase.logLessonLinkClickedFromTip(lesson.id, tip.fileName)
      }
    }
    container.add(openLessonLink)
    container.add(Box.createRigidArea(JBDimension(12, 28)))

    val wrapper = JPanel()
    wrapper.layout = BoxLayout(wrapper, BoxLayout.X_AXIS)
    wrapper.background = UIUtil.getTextFieldBackground()
    wrapper.border = JBEmptyBorder(8, 12, 8, 12)
    wrapper.add(container)
    return wrapper
  }
}
