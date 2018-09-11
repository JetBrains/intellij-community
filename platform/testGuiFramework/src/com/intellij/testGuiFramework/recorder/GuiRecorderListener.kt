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
      EP_NAME.extensionList.forEach { it.beforeRecordingStart() }
    }

    internal fun notifyBeforeRecordingPause() {
      EP_NAME.extensionList.forEach { it.beforeRecordingPause() }
    }

    internal fun notifyBeforeRecordingFinish() {
      EP_NAME.extensionList.forEach { it.beforeRecordingFinish() }
    }

    internal fun notifyRecordingStarted() {
      EP_NAME.extensionList.forEach { it.recordingStarted() }
    }

    internal fun notifyRecordingPaused() {
      EP_NAME.extensionList.forEach { it.recordingPaused() }
    }

    internal fun notifyRecordingFinished() {
      EP_NAME.extensionList.forEach { it.recordingFinished() }
    }
  }
}
