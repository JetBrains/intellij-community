// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.util.TipAndTrickBean
import com.intellij.ide.util.TipAndTrickManager
import com.intellij.ide.util.TipAndTrickPromotionFactory
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.ClientProperty
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.panels.BackgroundRoundedPanel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import training.learn.CourseManager
import training.learn.LearnBundle
import training.learn.course.LearningCourse
import training.statistic.LessonStartingWay
import training.statistic.StatisticBase
import training.util.enableLessonsAndPromoters
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel

class IftTipAndTrickPromoter : TipAndTrickPromotionFactory {
  override fun createPromotionPanel(project: Project, tip: TipAndTrickBean): JPanel? {
    if (!enableLessonsAndPromoters) return null
    val lessonId = findLessonIdForTip(tip) ?: return null
    return createOpenLessonPanel(project, lessonId, tip)
  }

  private fun findLessonIdForTip(tip: TipAndTrickBean): String? {
    val course: LearningCourse = CourseManager.instance.currentCourse ?: return null
    val lessonIdToTipsMap = course.getLessonIdToTipsMap()
    val lessonIds = lessonIdToTipsMap.filterValues { it.contains(tip.id) }.keys
    if (lessonIds.isNotEmpty()) {
      if (lessonIds.size > 1) {
        thisLogger().warn("$tip declared as suitable in more than one lesson: $lessonIds")
      }
      return lessonIds.first()
    }
    return null
  }

  private fun createOpenLessonPanel(project: Project, lessonId: String, tip: TipAndTrickBean): JPanel {
    return if (ExperimentalUI.isNewUI()) {
      createPanelForNewUI(project, lessonId, tip)
    }
    else createPanel(project, lessonId, tip)
  }

  private fun createPanelForNewUI(project: Project, lessonId: String, tip: TipAndTrickBean): JPanel {
    val panel = EditorNotificationPanel(EditorNotificationPanel.Status.Info)
    panel.text = LearnBundle.message("tip.and.trick.promotion.label")
    panel.createActionLabel(LearnBundle.message("tip.and.trick.promotion.open.lesson")) {
      openLesson(project, lessonId, tip)
    }
    val insideBorder = panel.border
    val outsideBorder = ClientProperty.get(panel, FileEditorManager.SEPARATOR_BORDER)
    panel.border = JBUI.Borders.compound(outsideBorder, insideBorder)
    return panel
  }

  private fun createPanel(project: Project, lessonId: String, tip: TipAndTrickBean): JPanel {
    val container = BackgroundRoundedPanel(8)
    container.layout = BoxLayout(container, BoxLayout.X_AXIS)
    container.background = UISettings.getInstance().shortcutBackgroundColor

    val promotionLabel = JLabel(LearnBundle.message("tip.and.trick.promotion.label"))
    promotionLabel.icon = AllIcons.General.BalloonInformation
    promotionLabel.iconTextGap = JBUI.scale(6)
    container.add(Box.createRigidArea(JBDimension(12, 28)))
    container.add(promotionLabel)
    container.add(Box.createHorizontalGlue())

    val openLessonLink = ActionLink(LearnBundle.message("tip.and.trick.promotion.open.lesson")) {
      openLesson(project, lessonId, tip)
    }
    container.add(openLessonLink)
    container.add(Box.createRigidArea(JBDimension(12, 28)))

    val wrapper = JPanel()
    wrapper.layout = BoxLayout(wrapper, BoxLayout.X_AXIS)
    wrapper.background = UIUtil.getTextFieldBackground()
    wrapper.border = JBEmptyBorder(8, 12, 3, 12)
    wrapper.add(container)
    return wrapper
  }

  private fun openLesson(project: Project, lessonId: String, tip: TipAndTrickBean) {
    TipAndTrickManager.getInstance().closeTipDialog()
    if (project.isDisposed) return

    val courseManager = CourseManager.instance
    val lesson = courseManager.lessonsForModules.find { it.id == lessonId }
    if (lesson == null) {
      thisLogger().error("Not found lesson with id: $lessonId")
      return
    }

    courseManager.openLesson(project, lesson, LessonStartingWay.TIP_AND_TRICK_PROMOTER, forceStartLesson = true)
    StatisticBase.logLessonLinkClickedFromTip(lessonId, tip.id)
  }
}
