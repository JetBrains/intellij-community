// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde.coursesInProgress

import com.intellij.openapi.wm.InteractiveCourseFactory
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.InteractiveCoursePanel
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.jbAcademy.JBAcademyWelcomeScreenBundle
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

class OtherCoursesPanel(private val courseFactory: InteractiveCourseFactory) : Wrapper() {

  init {
    val panel = panel {
      row {
        label(JBAcademyWelcomeScreenBundle.message("welcome.tab.learn.other.courses")).apply {
          component.font = component.font.deriveFont(Font.BOLD, 16.0f)
        }
      }
      row {
        cell(createMainPanel(courseFactory))
        if (!courseFactory.isEnabled) {
          rowComment(
            courseFactory.disabledText,
            action = HyperlinkEventAction { ExperimentalUI.setNewUI(true) }
          )
        }
      }
    }
    setContent(panel)
    border = JBUI.Borders.emptyRight(10)
  }

  private fun createMainPanel(courseFactory: InteractiveCourseFactory): JPanel {
    val mainPanel = JPanel()
    mainPanel.layout = BoxLayout(mainPanel, BoxLayout.X_AXIS)
    mainPanel.alignmentY = CENTER_ALIGNMENT
    mainPanel.add(OtherCoursePanel())

    val buttonPanel = JPanel()
    buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.Y_AXIS)
    buttonPanel.add(Box.createVerticalGlue())
    buttonPanel.add(JButton(JBAcademyWelcomeScreenBundle.message("welcome.tab.learn.start.learning")).apply {
      action = courseFactory.getCourseData().getAction()
      isEnabled = courseFactory.isEnabled
    })
    buttonPanel.add(Box.createVerticalGlue())

    mainPanel.add(buttonPanel)
    return mainPanel
  }

  private inner class OtherCoursePanel : InteractiveCoursePanel(courseFactory.getCourseData(), courseFactory.isEnabled) {
    // we want to add a button in one line with course description, not under it
    override fun createSouthPanel(): JPanel = JPanel()
  }
}
