// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.componentTesting.canvas

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import javax.swing.JComponent

interface ComponentToTest {
  fun build(disposable: Disposable): JComponent
  fun getFrameWidth(): Int
  fun getFrameHeight(): Int
}

internal object ComponentTesting {
  private lateinit var frame: UiTestFrameWrapper
  fun show(componentToTest: ComponentToTest) {
    runInEdt {
      frame = UiTestFrameWrapper()
      frame.show(componentToTest)
    }
  }

  fun close() {
    runInEdt {
      frame.close()
    }
  }
}