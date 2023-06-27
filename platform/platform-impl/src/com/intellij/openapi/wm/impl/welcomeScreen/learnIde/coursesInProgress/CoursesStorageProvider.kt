// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.learnIde.coursesInProgress

import com.intellij.openapi.extensions.ExtensionPointName

interface CoursesStorageProvider {
  fun getCoursesStorage(): CoursesStorage

  companion object {
    val COURSE_STORAGE_PROVIDER_EP = ExtensionPointName<CoursesStorageProvider>("com.intellij.coursesStorageProvider")

    fun getAllStorages(): List<CoursesStorage> {
      return COURSE_STORAGE_PROVIDER_EP.extensions.map { it.getCoursesStorage() }
    }
  }
}