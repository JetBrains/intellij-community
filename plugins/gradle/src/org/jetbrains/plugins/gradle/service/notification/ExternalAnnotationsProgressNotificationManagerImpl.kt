// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.notification

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.EventDispatcher

class ExternalAnnotationsProgressNotificationManagerImpl : ExternalAnnotationsProgressNotificationManager {
  private val dispatcher = EventDispatcher.create(ExternalAnnotationsProgressNotificationListener::class.java)

  override fun addNotificationListener(listener: ExternalAnnotationsProgressNotificationListener): Boolean {
    return addListener(listener)
  }

  override fun addNotificationListener(listener: ExternalAnnotationsProgressNotificationListener, parentDisposable: Disposable): Boolean {
    return addNotificationListener(listener, parentDisposable)
  }

  override fun removeNotificationListener(listener: ExternalAnnotationsProgressNotificationListener) {
    dispatcher.removeListener(listener)
  }

  override fun onStartResolve(id: ExternalAnnotationsTaskId) {
    dispatcher.listeners.forEach { it.onStartResolve(id) }
  }

  override fun onFinishResolve(id: ExternalAnnotationsTaskId) {
    dispatcher.listeners.forEach { it.onFinishResolve(id) }
  }

  private fun addListener(listener: ExternalAnnotationsProgressNotificationListener, parentDisposable: Disposable? = null): Boolean {
    if(dispatcher.listeners.contains(listener)) return false
    if(parentDisposable == null) {
      dispatcher.addListener(listener)
    } else {
      dispatcher.addListener(listener, parentDisposable)
    }
    return true
  }

  companion object {
    fun getInstanceImpl(): ExternalAnnotationsProgressNotificationManagerImpl {
      return ApplicationManager.getApplication()
        .getService(ExternalAnnotationsProgressNotificationManager::class.java) as ExternalAnnotationsProgressNotificationManagerImpl
    }
  }
}