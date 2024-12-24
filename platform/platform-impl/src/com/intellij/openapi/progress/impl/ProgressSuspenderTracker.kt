// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.util.application

@Service
internal class ProgressSuspenderTracker : ProgressSuspender.SuspenderListener {
  private val suspenderTrackers = mutableMapOf<ProgressSuspender, SuspenderListener>()

  init {
    application.messageBus.connect().subscribe(ProgressSuspender.TOPIC, this)
  }

  fun startTracking(progressSuspender: ProgressSuspender, listener: SuspenderListener) {
    suspenderTrackers[progressSuspender] = listener
  }

  fun stopTracking(progressSuspender: ProgressSuspender) {
    suspenderTrackers.remove(progressSuspender)
  }

  override fun suspendedStatusChanged(suspender: ProgressSuspender) {
    suspenderTrackers[suspender]?.onStateChanged()
  }

  companion object {
    fun getInstance(): ProgressSuspenderTracker = service()
  }

  fun interface SuspenderListener {
    fun onStateChanged()
  }
}