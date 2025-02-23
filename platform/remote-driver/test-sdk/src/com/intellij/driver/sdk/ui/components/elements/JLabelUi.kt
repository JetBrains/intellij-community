package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

class JLabelUiComponent(data: ComponentData) : UiComponent(data) {

  private val fixture by lazy { driver.cast(component, JLabelRef::class) }

  fun getText() = fixture.getText()
}

@Remote("javax.swing.JLabel")
interface JLabelRef {

  fun getText(): String
}