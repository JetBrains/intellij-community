// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.openapi.wm.impl.welcomeScreen.learnIde.coursesInProgress

import com.intellij.icons.AllIcons
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

interface CourseDataStorage {

  fun getCoursePath(courseInfo: CourseInfo): String?

  fun removeCourseByLocation(location: String): Boolean

  fun getAllCourses(): List<CourseInfo>
}

open class CourseInfo {
  var tasksTotal: Int = 0
  var tasksSolved: Int = 0
  var description: String = ""
  var name: String = ""
  // remove when implemented properly in JBA and IFT plugins
  open var icon: Icon? = AllIcons.Welcome.LearnTab.JetBrainsAcademy
  var location: String = ""
  var id: Int = -1
}

val COURSE_DELETED = Topic.create("JetBrainsAcademy.courseDeletedFromStorage", CourseDeletedListener::class.java)