// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.openapi.wm.impl.welcomeScreen.learnIde.coursesInProgress

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.wm.InteractiveCourseFactory
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.*
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.jbAcademy.JBAcademyWelcomeScreenBundle
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import javax.swing.JScrollPane

@ApiStatus.Internal
class CoursesInProgressPanel : Wrapper() {

  init {
    val coursesStorages = CoursesStorageProvider.getAllStorages()
    val coursesInProgress = coursesStorages.flatMap { it.getAllCourses() }

    val coursesListPanel = CoursesListPanel(coursesInProgress)
    coursesListPanel.setClickListener { courseData ->
      val coursePath = getCoursePath(courseData) ?: return@setClickListener true
      if (!FileUtil.exists(coursePath)) {
        if (showNoCourseDialog(coursePath, JBAcademyWelcomeScreenBundle.message("welcome.tab.learn.remove.course.title")) == Messages.NO) {
          coursesStorages.any {
            it.removeCourseByLocation(coursePath)
          }
          ApplicationManager.getApplication().messageBus.syncPublisher(COURSE_DELETED).courseDeleted(courseData)
        }
      }
      else {
        openCourse(courseData)
      }
      true
    }

    val contentPanel = createContentPanel(coursesListPanel)

    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(COURSE_DELETED, object : CourseDeletedListener {
      override fun courseDeleted(course: CourseInfo) {
        coursesListPanel.updatePanel(CoursesStorageProvider.getAllStorages().flatMap { it.getAllCourses() })
        revalidate()
        repaint()
      }
    })

    setContent(contentPanel)
    UIUtil.setBackgroundRecursively(this, mainBackgroundColor)
  }

  private fun createContentPanel(coursesListPanel: CoursesListPanel) = panel {
    row {
      cell(createTitlePanel(JBAcademyWelcomeScreenBundle.message("welcome.tab.learn.continue.learning")))

      val browseCoursesAction = getBrowseCoursesAction()
      if (browseCoursesAction != null) {
        val button = LearnButton(browseCoursesAction,
                                 JBAcademyWelcomeScreenBundle.message("welcome.tab.learn.start.new.course"),
                                 true)
        cell(button).align(AlignX.RIGHT)
      }
      topGap(TopGap.MEDIUM)
    }
    row {
      topGap(TopGap.SMALL)
      scrollCell(coursesListPanel).align(Align.FILL).applyToComponent {
        val parentScroll = (parent.parent as JScrollPane)
        parentScroll.border = JBUI.Borders.empty()
      }
      resizableRow()
    }
    row {
      val interactiveCourseFactories = InteractiveCourseFactory.INTERACTIVE_COURSE_FACTORY_EP.extensions
      val courseFactory = interactiveCourseFactories.find { !it.getCourseData().isEduTools() }
      if (courseFactory != null) {
        topGap(TopGap.SMALL)
        cell(OtherCoursesPanel(courseFactory))
      }
    }
    row {
      val helpAndResourcesPanel = HelpAndResourcesPanel()
      cell(helpAndResourcesPanel)
      alignmentY = BOTTOM_ALIGNMENT
      topGap(TopGap.MEDIUM)
    }
  }.apply {
    border = JBUI.Borders.empty(0,32, 40, 32)
  }

  private fun openCourse(courseInfo: CourseInfo) {
    val coursePath = getCoursePath(courseInfo) ?: return
    val project = ProjectUtil.openProject(coursePath, null, true)
    ProjectUtil.focusProjectWindow(project, true)
  }

  private fun getCoursePath(courseData: CourseInfo): String? {
    val storageProviders = CoursesStorageProvider.COURSE_STORAGE_PROVIDER_EP.extensions
    val coursesStorages = storageProviders.map { it.getCoursesStorage() }
    return coursesStorages.map {
      it.getCoursePath(courseData)
    }.firstOrNull()
  }

  private fun showNoCourseDialog(coursePath: String, cancelButtonText: String): Int {
    return Messages.showDialog(null,
                               JBAcademyWelcomeScreenBundle.message("welcome.tab.learn.course.not.found.text",
                                                                    FileUtil.toSystemDependentName(coursePath)),
                               JBAcademyWelcomeScreenBundle.message("welcome.tab.learn.course.not.found.title"),
                               arrayOf(Messages.getCancelButton(), cancelButtonText),
                               Messages.OK,
                               Messages.getErrorIcon())
  }
}

fun createTitlePanel(titleText: String): HeightLimitedPane {
  return HeightLimitedPane(titleText, 3, LearnIdeContentColorsAndFonts.HeaderColor, true)
}