package com.intellij.driver.sdk.jbacademy

import com.intellij.driver.sdk.jbacademy.JBAcademyConstants.CHECK_PANEL_BUTTON_XPATH
import com.intellij.driver.sdk.jbacademy.JBAcademyConstants.CONTENT_TAB_LABEL_XPATH
import com.intellij.driver.sdk.jbacademy.JBAcademyConstants.COURSE_ACCESSIBLENAME_XPATH
import com.intellij.driver.sdk.jbacademy.JBAcademyConstants.COURSE_TOOL_WINDOW_XPATH
import com.intellij.driver.sdk.jbacademy.JBAcademyConstants.DESCRIPTION_TAB_XPATH
import com.intellij.driver.sdk.jbacademy.JBAcademyConstants.LESSON_ACCESSIBLENAME_XPATH
import com.intellij.driver.sdk.jbacademy.JBAcademyConstants.LESSON_HEADER_XPATH
import com.intellij.driver.sdk.jbacademy.JBAcademyConstants.PREVIEW_COURSE_XPATH
import com.intellij.driver.sdk.jbacademy.JBAcademyConstants.STATUS_BAR_ICON_XPATH
import com.intellij.driver.sdk.jbacademy.JBAcademyConstants.TASK_ACCESSIBLENAME_XPATH
import com.intellij.driver.sdk.jbacademy.JBAcademyConstants.TASK_TOOL_WINDOW_XPATH
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI

fun Finder.courseTree(action: CourseTreeView.() -> Unit = {}): CourseTreeView {
  return x(COURSE_TOOL_WINDOW_XPATH, CourseTreeView::class.java).apply(action)
}

open class CourseTreeView(data: ComponentData) : UiComponent(data) {
  val courseTabLabel
    get() = x(COURSE_ACCESSIBLENAME_XPATH)

  val addLessonButton
    get() = x(LESSON_ACCESSIBLENAME_XPATH)

  val addTaskButton
    get() = x(TASK_ACCESSIBLENAME_XPATH)

  val previewCourseButton
    get() = x(PREVIEW_COURSE_XPATH)
}

fun IdeaFrameUI.statusBarIconButton(): UiComponent = x(STATUS_BAR_ICON_XPATH)

fun IdeaFrameUI.taskToolWindow(action: TaskToolWindow.() -> Unit = {}): TaskToolWindow {
  return x(TASK_TOOL_WINDOW_XPATH, TaskToolWindow::class.java).apply(action)
}

open class TaskToolWindow(data: ComponentData) : UiComponent(data) {
  val contentTabLabel
    get() = x(CONTENT_TAB_LABEL_XPATH)

  val lessonHeader
    get() = x(LESSON_HEADER_XPATH)

  val descriptionTab
    get() = x(DESCRIPTION_TAB_XPATH)

  val checkButton
    get() = x(CHECK_PANEL_BUTTON_XPATH)
}

fun TaskToolWindow.descriptionTab(action: DescriptionTab.() -> Unit = {}): DescriptionTab {
  return x(DESCRIPTION_TAB_XPATH, DescriptionTab::class.java).apply(action)
}

open class DescriptionTab(data: ComponentData) : UiComponent(data) {}