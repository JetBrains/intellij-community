// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.recorder

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions

interface GuiRecorderListener {

  fun beforeRecordingStart() = Unit

  fun beforeRecordingPause() = Unit

  fun beforeRecordingFinish() = Unit

  fun recordingStarted() = Unit

  fun recordingPaused() = Unit

  fun recordingFinished() = Unit

  companion object {
    val EP_NAME = ExtensionPointName.create<GuiRecorderListener>("com.intellij.guiRecorderListener")

    internal fun notifyBeforeRecordingStart() {
      for (listener in Extensions.getExtensions(EP_NAME)) {
        listener.beforeRecordingStart()
      }
    }

    internal fun notifyBeforeRecordingPause() {
      for (listener in Extensions.getExtensions(EP_NAME)) {
        listener.beforeRecordingPause()
      }
    }

    internal fun notifyBeforeRecordingFinish() {
      for (listener in Extensions.getExtensions(EP_NAME)) {
        listener.beforeRecordingFinish()
      }
    }

    internal fun notifyRecordingStarted() {
      for (listener in Extensions.getExtensions(EP_NAME)) {
        listener.recordingStarted()
      }
    }

    internal fun notifyRecordingPaused() {
      for (listener in Extensions.getExtensions(EP_NAME)) {
        listener.recordingPaused()
      }
    }

    internal fun notifyRecordingFinished() {
      for (listener in Extensions.getExtensions(EP_NAME)) {
        listener.recordingFinished()
      }
    }
  }
}
