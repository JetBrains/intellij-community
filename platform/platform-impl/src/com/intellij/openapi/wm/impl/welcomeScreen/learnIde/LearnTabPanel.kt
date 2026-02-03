// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.impl.welcomeScreen.learnIde.coursesInProgress.*
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class LearnTabPanel(private val parentDisposable: Disposable) : Wrapper() {

  init {
    layoutPanel()
    val connection = ApplicationManager.getApplication().messageBus.connect(parentDisposable)
    connection.subscribe(COURSE_DELETED, object : CourseDeletedListener {
      override fun courseDeleted(course: CourseInfo) {
        layoutPanel()
      }
    })
  }

  private fun layoutPanel() {
    removeAll()
    setContent(
      if (hasCoursesInProgress()) {
        CoursesInProgressPanel()
      }
      else {
        LearnIdeContentPanel(parentDisposable)
      }
    )

    isOpaque = true
    UIUtil.setBackgroundRecursively(this, mainBackgroundColor)
  }

  private fun hasCoursesInProgress(): Boolean {
    CoursesStorageProvider.getAllStorages().forEach {
      if (it.getAllCourses().isNotEmpty()) {
        return true
      }
    }

    return false
  }
}