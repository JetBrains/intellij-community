// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.componentTesting

import com.intellij.ui.components.JBLabel
import com.intellij.uiTests.componentTesting.canvas.ComponentToTest
import javax.swing.JComponent

internal class DemoComponentToTest: ComponentToTest {
  override fun build(): JComponent {
    return JBLabel("My test component")
  }
}