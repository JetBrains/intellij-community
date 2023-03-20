// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.componentTesting.canvas

import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import java.awt.event.WindowEvent
import javax.swing.JFrame

internal class UiTestFrameWrapper {
  private lateinit var frame : UITestFrame

  fun show(componentToTest: ComponentToTest) {
    frame = UITestFrame().apply {
      title = componentToTest::class.java.canonicalName
      //extendedState = JFrame.MAXIMIZED_BOTH;
      setSize(componentToTest.getFrameWidth(), componentToTest.getFrameHeight())
      val componentToAdd = componentToTest.build(this)
      glassPane = IdeGlassPaneImpl(rootPane)
      add(componentToAdd)
      isVisible = true
    }
  }

  fun close() {
    frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSING))
  }
}

class UITestFrame(): JFrame(), Disposable