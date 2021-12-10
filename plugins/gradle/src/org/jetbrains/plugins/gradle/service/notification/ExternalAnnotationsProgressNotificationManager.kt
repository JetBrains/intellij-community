// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.notification

import com.intellij.openapi.application.ApplicationManager

interface ExternalAnnotationsProgressNotificationManager {
  fun addNotificationListener(listener: ExternalAnnotationsProgressNotificationListener)
  fun removeNotificationListener(listener: ExternalAnnotationsProgressNotificationListener)

  fun onStartResolve(id: ExternalAnnotationsTaskId)

  fun onCancelResolve(id: ExternalAnnotationsTaskId)

  companion object {
    fun getInstance(): ExternalAnnotationsProgressNotificationManager {
      return ApplicationManager.getApplication().getService(ExternalAnnotationsProgressNotificationManager::class.java)
    }
  }
}