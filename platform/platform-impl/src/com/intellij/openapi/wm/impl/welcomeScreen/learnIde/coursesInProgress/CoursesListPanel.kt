// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde.coursesInProgress

import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.JPanel


@ApiStatus.Internal
class CoursesListPanel(courses: List<CourseInfo>) : JPanel(VerticalFlowLayout(0, 0)) {
  private val coursesModel: CoursesModel = CoursesModel()

  init {
    background = mainBackgroundColor
    border = JBUI.Borders.empty()

    updatePanel(courses)
  }

  fun updatePanel(courses: List<CourseInfo>) {
    removeAll()
    for (course in courses) {
      val courseCardComponent = CourseCardComponent(course)
      courseCardComponent.updateColors(mainBackgroundColor)
      coursesModel.addCourseCard(courseCardComponent)
      add(courseCardComponent)
    }
    revalidate()
    repaint()
  }

  fun setClickListener(onClick: (CourseInfo) -> Boolean) {
    coursesModel.onClick = onClick
  }
}