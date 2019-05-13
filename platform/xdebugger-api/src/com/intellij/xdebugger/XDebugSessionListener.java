// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger;

import java.util.EventListener;

/**
 * @author nik
 */
public interface XDebugSessionListener extends EventListener {
  default void sessionPaused() {
  }

  default void sessionResumed() {
  }

  default void sessionStopped() {
  }

  default void stackFrameChanged() {
  }

  default void beforeSessionResume() {
  }

  default void settingsChanged() {
  }
}
