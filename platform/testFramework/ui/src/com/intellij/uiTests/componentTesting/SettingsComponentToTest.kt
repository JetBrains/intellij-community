// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.componentTesting

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurableWithId
import com.intellij.openapi.options.ex.ConfigurableCardPanel
import com.intellij.uiTests.componentTesting.canvas.ComponentToTest
import javax.swing.JComponent

class SettingsComponentToTest(private val id: String): ComponentToTest {
  override fun build(disposable: Disposable): JComponent {
    val configurable = ShowSettingsUtilImpl.getConfigurables(null, true).filterIsInstance<ConfigurableWithId>().first { it.id == id }
    return ConfigurableCardPanel.createConfigurableComponent(configurable)
  }

  override fun getFrameWidth(): Int = 800

  override fun getFrameHeight(): Int = 600
}