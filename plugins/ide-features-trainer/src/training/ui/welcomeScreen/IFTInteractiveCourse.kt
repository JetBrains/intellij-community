// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.ui.welcomeScreen

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.wm.InteractiveCourseData
import com.intellij.openapi.wm.InteractiveCourseFactory
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.HeightLimitedPane
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.LearnIdeContentColorsAndFonts.MODULE_DESCRIPTION
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import icons.FeaturesTrainerIcons.Img.PluginIcon
import training.actions.OpenLessonAction
import training.actions.StartLearnAction
import training.learn.CourseManager
import training.learn.LearnBundle
import training.learn.interfaces.Module
import java.awt.Component
import java.awt.Component.LEFT_ALIGNMENT
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.plaf.FontUIResource
import javax.swing.plaf.LabelUI

class IFTInteractiveCourse : InteractiveCourseFactory {
  override fun getInteractiveCourseData(): InteractiveCourseData = IFTInteractiveCourseData()
}

class IFTInteractiveCourseData : InteractiveCourseData {

  override fun getName(): String {
    return LearnBundle.message("welcome.tab.header.learn.ide.features")
  }

  override fun getDescription(): String {
    return LearnBundle.message("welcome.tab.description.learn.ide.features")
  }

  override fun getIcon(): Icon {
    return PluginIcon
  }

  override fun getActionButtonName(): String {
    return LearnBundle.message("welcome.tab.start.learning.button")
  }

  override fun getAction(): Action {
    return object : AbstractAction(LearnBundle.message("welcome.tab.start.learning.button")) {
      override fun actionPerformed(e: ActionEvent?) {
        performActionOnWelcomeScreen(StartLearnAction())
      }
    }
  }

  override fun getExpandContent(): JComponent {
    return modulesPanel()
  }

  private fun modulesPanel(): JPanel {
    val modules = CourseManager.instance.modules
    val panel = JPanel()
    panel.isOpaque = false
    panel.layout = BoxLayout(panel, BoxLayout.PAGE_AXIS)

    panel.add(rigid(16, 1))
    for (module in modules) {
      panel.add(moduleHeader(module))
      panel.add(rigid(2, 2))
      panel.add(moduleDescription(module))
      panel.add(rigid(16, 16))
    }
    panel.add(rigid(16, 15))
    return panel
  }

  private fun moduleDescription(module: Module): HeightLimitedPane {
    return HeightLimitedPane(module.description ?: "", -2, MODULE_DESCRIPTION)
  }

  private fun moduleHeader(module: Module): LinkLabel<Any> {
    val linkLabel = object : LinkLabel<Any>(module.name, null) {
      override fun setUI(ui: LabelUI?) {
        super.setUI(ui)
        if (font != null) {
          font = FontUIResource(font.deriveFont(font.size2D + JBUIScale.scale(-1)))
        }
      }
    }
    linkLabel.name = "linkLabel.${module.name}"
    linkLabel.setListener(
      { _, _ ->
        var lesson = module.giveNotPassedLesson()
        if (lesson == null) lesson = module.lessons[0]
        performActionOnWelcomeScreen(OpenLessonAction(lesson))
      }, null)
    return linkLabel
  }

  private fun rigid(_width: Int, _height: Int): Component {
    return Box.createRigidArea(
      Dimension(JBUI.scale(_width), JBUI.scale(_height))).apply { (this as JComponent).alignmentX = LEFT_ALIGNMENT }
  }

  private fun performActionOnWelcomeScreen(action: AnAction) {
    val anActionEvent = AnActionEvent.createFromAnAction(action, null, ActionPlaces.WELCOME_SCREEN, DataContext.EMPTY_CONTEXT)
    ActionUtil.performActionDumbAware(action, anActionEvent)
  }

}


