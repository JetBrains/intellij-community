// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.componentTesting.canvas

import com.intellij.openapi.application.runInEdt
import javax.swing.JComponent

internal fun interface ComponentToTest {
  fun build(): JComponent
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