// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.notification

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager

interface ExternalAnnotationsProgressNotificationManager {
  fun addNotificationListener(listener: ExternalAnnotationsProgressNotificationListener): Boolean

  fun addNotificationListener(listener: ExternalAnnotationsProgressNotificationListener, parentDisposable: Disposable): Boolean

  fun removeNotificationListener(listener: ExternalAnnotationsProgressNotificationListener)

  fun onStartResolve(id: ExternalAnnotationsTaskId)

  fun onFinishResolve(id: ExternalAnnotationsTaskId)

  companion object {
    fun getInstance(): ExternalAnnotationsProgressNotificationManager {
      return ApplicationManager.getApplication().getService(ExternalAnnotationsProgressNotificationManager::class.java)
    }
  }
}