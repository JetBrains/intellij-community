// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.application
import java.util.concurrent.ConcurrentHashMap

@Service
internal class ProgressSuspenderTracker : ProgressSuspender.SuspenderListener, Disposable {
  private val suspenderTrackers = ConcurrentHashMap<ProgressSuspender, SuspenderListener>()
  private val indicatorTrackers = ConcurrentHashMap<ProgressIndicator, IndicatorListener>()

  init {
    application.messageBus.connect(this).subscribe(ProgressSuspender.TOPIC, this)
  }

  fun startTracking(progressSuspender: ProgressSuspender, listener: SuspenderListener) {
    suspenderTrackers[progressSuspender] = listener
  }

  fun stopTracking(progressSuspender: ProgressSuspender) {
    suspenderTrackers.remove(progressSuspender)
  }

  fun startTracking(indicator: ProgressIndicator, listener: IndicatorListener) {
    indicatorTrackers[indicator] = listener
  }

  fun stopTracking(indicator: ProgressIndicator) {
    indicatorTrackers.remove(indicator)
  }

  override fun suspendedStatusChanged(suspender: ProgressSuspender) {
    suspenderTrackers[suspender]?.onStateChanged(suspender)
  }

  override fun suspendableProgressAppeared(suspender: ProgressSuspender, progressIndicator: ProgressIndicator) {
    val listener = indicatorTrackers[progressIndicator] ?: return
    listener.suspenderAdded(suspender)
    startTracking(suspender, listener)
  }

  override fun suspendableProgressRemoved(suspender: ProgressSuspender, progressIndicator: ProgressIndicator) {
    val listener = indicatorTrackers[progressIndicator] ?: return
    stopTracking(suspender)
    listener.suspenderRemoved()
  }

  override fun dispose() {
    suspenderTrackers.clear()
    indicatorTrackers.clear()
  }

  companion object {
    fun getInstance(): ProgressSuspenderTracker = service()
  }

  interface SuspenderListener {
    fun onStateChanged(suspender: ProgressSuspender)
  }

  interface IndicatorListener : SuspenderListener {
    fun suspenderAdded(suspender: ProgressSuspender)

    fun suspenderRemoved()
  }
}