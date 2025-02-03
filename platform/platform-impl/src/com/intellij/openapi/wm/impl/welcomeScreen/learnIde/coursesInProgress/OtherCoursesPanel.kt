// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde.coursesInProgress

import com.intellij.openapi.wm.InteractiveCourseFactory
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.InteractiveCoursePanel
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.LearnButton
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.jbAcademy.JBAcademyWelcomeScreenBundle
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.JPanel

@ApiStatus.Internal
class OtherCoursesPanel(private val courseFactory: InteractiveCourseFactory) : Wrapper() {

  init {
    val panel = panel {
      row {
        cell(createTitlePanel(JBAcademyWelcomeScreenBundle.message("welcome.tab.learn.other.courses")))
        bottomGap(BottomGap.SMALL)
      }
      row {
        cell(createMainPanel(courseFactory))
        if (!courseFactory.isEnabled) {
          rowComment(courseFactory.disabledText)
        }
      }
    }
    setContent(panel)
    border = JBUI.Borders.emptyRight(10)
  }

  private fun createMainPanel(courseFactory: InteractiveCourseFactory): JPanel {
    return panel {
      row {
        cell(OtherCoursePanel()).align(Align.FILL).gap(RightGap.COLUMNS).resizableColumn()

        val button = LearnButton(courseFactory.getCourseData().getAction(), courseFactory.isEnabled)

        cell(button).align(AlignY.TOP).align(AlignX.RIGHT).apply {
          border = JBUI.Borders.empty(6, 0)
        }
      }
    }
  }

  private inner class OtherCoursePanel : InteractiveCoursePanel(courseFactory.getCourseData(), courseFactory.isEnabled) {
    // we want to add a button in one line with course description, not under it
    override fun createSouthPanel(): JPanel = JPanel()
  }
}
