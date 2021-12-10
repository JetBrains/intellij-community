// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.notification

import com.intellij.openapi.application.ApplicationManager

class ExternalAnnotationsProgressNotificationManagerImpl : ExternalAnnotationsProgressNotificationManager {
  private val listeners: MutableList<ExternalAnnotationsProgressNotificationListener> = mutableListOf()
  override fun addNotificationListener(listener: ExternalAnnotationsProgressNotificationListener) {
    listeners += listener
  }

  override fun removeNotificationListener(listener: ExternalAnnotationsProgressNotificationListener) {
    listeners -= listener
  }

  override fun onStartResolve(id: ExternalAnnotationsTaskId) {
    listeners.forEach { it.onStartResolve(id) }
  }

  override fun onCancelResolve(id: ExternalAnnotationsTaskId) {
    listeners.forEach { it.onCancelResolve(id) }
  }
  companion object {
    fun getInstanceImpl(): ExternalAnnotationsProgressNotificationManagerImpl {
      return ApplicationManager.getApplication()
        .getService(ExternalAnnotationsProgressNotificationManager::class.java) as ExternalAnnotationsProgressNotificationManagerImpl
    }
  }
}