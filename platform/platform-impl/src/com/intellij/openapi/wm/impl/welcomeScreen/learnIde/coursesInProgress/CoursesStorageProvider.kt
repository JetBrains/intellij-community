// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde.coursesInProgress

import com.intellij.openapi.extensions.ExtensionPointName

/**
 *  Extension point to provide custom [CourseDataStorage] to show in-progress course
 *  on [com.intellij.openapi.wm.impl.welcomeScreen.learnIde.LearnTabPanel]
 *
 *  Currently used in [JetBrains Academy Plugin](https://plugins.jetbrains.com/plugin/10081-jetbrains-academy)
 */
interface CoursesStorageProvider {
  fun getCoursesStorage(): CourseDataStorage

  companion object {
    val COURSE_STORAGE_PROVIDER_EP = ExtensionPointName<CoursesStorageProvider>("com.intellij.coursesStorageProvider")

    fun getAllStorages(): List<CourseDataStorage> {
      return COURSE_STORAGE_PROVIDER_EP.extensions.map { it.getCoursesStorage() }
    }
  }
}