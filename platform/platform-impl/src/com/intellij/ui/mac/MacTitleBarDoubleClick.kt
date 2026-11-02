// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac

import java.awt.Frame

internal object MacTitleBarDoubleClick {
  fun perform(frame: Frame) {
    val action = MacWindowPreferences.readString("AppleActionOnDoubleClick", "Apple Global Domain")
    if (action != null && action != "Maximize") {
      if (frame.extendedState == Frame.ICONIFIED) {
        frame.setExtendedState(Frame.NORMAL)
      }
      else {
        frame.setExtendedState(Frame.ICONIFIED)
      }
    }
    else {
      if (frame.extendedState == Frame.MAXIMIZED_BOTH) {
        frame.setExtendedState(Frame.NORMAL)
      }
      else {
        frame.setExtendedState(Frame.MAXIMIZED_BOTH)
      }
    }
  }
}
