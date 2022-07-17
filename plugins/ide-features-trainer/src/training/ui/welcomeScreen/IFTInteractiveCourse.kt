// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui.welcomeScreen

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.InteractiveCourseData
import com.intellij.openapi.wm.InteractiveCourseFactory
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.HeightLimitedPane
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.LearnIdeContentColorsAndFonts
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.scale.JBUIScale
import training.FeaturesTrainerIcons
import training.lang.LangManager
import training.learn.CourseManager
import training.learn.LearnBundle
import training.learn.OpenLessonActivities
import training.learn.course.IftModule
import training.learn.course.KLesson
import training.statistic.StatisticBase
import training.ui.views.NewContentLabel
import training.util.iftPluginIsUsing
import training.util.learningPanelWasOpenedInCurrentVersion
import training.util.rigid
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.plaf.FontUIResource
import javax.swing.plaf.LabelUI

internal class IFTInteractiveCourse : InteractiveCourseFactory {
  override fun getInteractiveCourseData(): InteractiveCourseData? =
    if (LangManager.getInstance().getLangSupport()?.useUserProjects == false) IFTInteractiveCourseData() else null
}

private class IFTInteractiveCourseData : InteractiveCourseData {

  override fun getName(): String {
    return LearnBundle.message("welcome.tab.header.learn.ide.features")
  }

  override fun getDescription(): String {
    return LearnBundle.message("welcome.tab.description.learn.ide.features")
  }

  override fun getIcon(): Icon {
    return FeaturesTrainerIcons.Img.PluginIcon
  }

  override fun getActionButtonName(): String {
    return LearnBundle.message("welcome.tab.start.learning.button")
  }

  override fun getAction(): Action {
    return object : AbstractAction(LearnBundle.message("welcome.tab.start.learning.button")) {
      override fun actionPerformed(e: ActionEvent?) {
        openLearningFromWelcomeScreen(null)
      }
    }
  }

  override fun getExpandContent(): JComponent {
    val modules = CourseManager.instance.modules
    val panel = JPanel()
    panel.isOpaque = false
    panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)

    panel.add(rigid(16, 1))
    for (module in modules) {
      val moduleHeader = moduleHeader(module)
      if (!moduleHasNewContent(module)) {
        panel.add(moduleHeader)
      } else {
        val nameLine = JPanel()
        nameLine.isOpaque = false
        nameLine.layout = BoxLayout(nameLine, BoxLayout.X_AXIS)
        nameLine.alignmentX = JPanel.LEFT_ALIGNMENT

        nameLine.add(moduleHeader)
        nameLine.add(rigid(10, 0))
        nameLine.add(NewContentLabel())

        panel.add(nameLine)
      }
      panel.add(rigid(2, 2))
      panel.add(moduleDescription(module))
      panel.add(rigid(16, 16))
    }
    panel.add(rigid(16, 15))
    StatisticBase.logWelcomeScreenPanelExpanded()
    return panel
  }

  private fun moduleHasNewContent(module: IftModule): Boolean {
    if (!iftPluginIsUsing) {
      return false
    }
    return module.lessons.any { it.isNewLesson() }
  }

  override fun newContentMarker(): JComponent? {
    if (learningPanelWasOpenedInCurrentVersion) {
      return null
    }
    if (CourseManager.instance.modules.any { moduleHasNewContent(it) }) {
      return NewContentLabel()
    }
    return null
  }

  private fun moduleDescription(module: IftModule): HeightLimitedPane {
    return HeightLimitedPane(module.description, -1, LearnIdeContentColorsAndFonts.ModuleDescriptionColor)
  }

  private fun moduleHeader(module: IftModule): LinkLabel<Any> {
    val linkLabel = object : LinkLabel<Any>(module.name, null) {
      override fun setUI(ui: LabelUI?) {
        super.setUI(ui)
        if (font != null) {
          font = FontUIResource(font.deriveFont(font.size2D + JBUIScale.scale(-1) + if (SystemInfo.isWindows) JBUIScale.scale(1) else 0))
        }
      }
    }
    linkLabel.name = "linkLabel.${module.name}"
    linkLabel.setListener(
      { _, _ ->
        StatisticBase.logModuleStarted(module)
        openLearningFromWelcomeScreen(module)
      }, null)
    return linkLabel
  }

  private fun openLearningFromWelcomeScreen(module: IftModule?) {
    LangManager.getInstance().getLangSupport()?.startFromWelcomeFrame { selectedSdk: Sdk? ->
      val onboardingLesson = findOnboardingLesson(module)
      if (onboardingLesson != null) {
        OpenLessonActivities.openOnboardingFromWelcomeScreen(onboardingLesson, selectedSdk)
      }
      else {
        CourseManager.instance.unfoldModuleOnInit = module ?: CourseManager.instance.modules.firstOrNull()
        OpenLessonActivities.openLearnProjectFromWelcomeScreen(selectedSdk)
      }
    }
  }

  private fun findOnboardingLesson(module: IftModule?): KLesson? {
    val firstLesson = module?.lessons?.singleOrNull()
    return firstLesson?.takeIf { it.id.contains("onboarding") }
  }
}
