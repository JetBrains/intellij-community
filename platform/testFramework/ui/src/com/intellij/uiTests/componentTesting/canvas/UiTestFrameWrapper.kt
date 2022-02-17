// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.componentTesting.canvas

import java.awt.event.WindowEvent
import javax.swing.JFrame

internal class UiTestFrameWrapper {
  private lateinit var frame : JFrame

  fun show(component: ComponentToTest) {
    val componentToAdd = component.build()

    frame = JFrame().apply {
      title = component::class.java.canonicalName
      extendedState = JFrame.MAXIMIZED_BOTH;
      add(componentToAdd)
      isVisible = true
    }
  }

  fun close() {
    frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSING))
  }
}