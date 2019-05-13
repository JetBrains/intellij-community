// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.recorder;

import com.intellij.openapi.extensions.ExtensionPointName;

public abstract class GuiRecorderListener {

  public abstract void beforeRecordingStart();

  public abstract void beforeRecordingPause();

  public abstract void beforeRecordingFinish();

  public abstract void recordingStarted();

  public abstract void recordingPaused();

  public abstract void recordingFinished();

  public static final ExtensionPointName<GuiRecorderListener> EP_NAME =
    new ExtensionPointName<>("com.intellij.guiRecorderListener");

  public static void notifyBeforeRecordingStart() {
    EP_NAME.getExtensionList().forEach(listener -> listener.beforeRecordingStart());
  }

  public static void notifyBeforeRecordingPause() {
    EP_NAME.getExtensionList().forEach(listener -> listener.beforeRecordingPause());
  }

  public static void notifyBeforeRecordingFinish() {
    EP_NAME.getExtensionList().forEach(listener -> listener.beforeRecordingFinish());
  }

  public static void notifyRecordingStarted() {
    EP_NAME.getExtensionList().forEach(listener -> listener.recordingStarted());
  }

  public static void notifyRecordingPaused() {
    EP_NAME.getExtensionList().forEach(listener -> listener.recordingPaused());
  }

  public static void notifyRecordingFinished() {
    EP_NAME.getExtensionList().forEach(listener -> listener.recordingFinished());
  }
}
