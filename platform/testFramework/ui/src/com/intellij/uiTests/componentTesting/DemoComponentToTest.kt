// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiTests.componentTesting

import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBLabel
import com.intellij.uiTests.componentTesting.canvas.ComponentToTest
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTextField

class DemoComponentToTest : ComponentToTest {
  override fun build(disposable: Disposable): JComponent {
    return DemoComponent(DemoModel())
  }

  override fun getFrameWidth(): Int = 800

  override fun getFrameHeight(): Int = 800
}

internal class DemoModel {
  var demoValue: String = "World"
}

internal class DemoComponent(private val model: DemoModel) : BorderLayoutPanel() {
  private val textField = JTextField().apply {
    text = model.demoValue
  }
  private val jLabel = JBLabel(model.demoValue)
  private val updateButton = JButton("Update").apply {
    addActionListener {
      this@DemoComponent.model.demoValue = textField.text
      jLabel.text = this@DemoComponent.model.demoValue
      this@DemoComponent.repaint()
    }
  }

  init {
    val panel = FormBuilder()
      .addLabeledComponent("Hello", textField)
      .addComponent(jLabel)
      .addComponent(updateButton)
      .panel
    addToCenter(panel)
  }
}